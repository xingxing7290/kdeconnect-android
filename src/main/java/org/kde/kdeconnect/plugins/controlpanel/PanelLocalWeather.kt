/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.content
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.kde.kdeconnect.helpers.JsonHelper
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Fills the idle dial with weather obtained by the phone itself.
 *
 * The desktop peer still owns the shared configuration and may push its own DIAL snapshot. This
 * class only provides a fallback for the case where no usable snapshot arrives: it resolves the
 * current place from the device location (falling back to the configured city name via the same
 * Open-Meteo geocoding service the desktop uses) and builds a snapshot through
 * [PanelWeatherSnapshot.parse], so every value keeps the protocol's validation rules. A failed
 * request never invents a temperature or a city: it either keeps the cached observation or returns
 * an explicit `unavailable` status.
 */
class PanelLocalWeather(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private data class Place(val latitude: Double, val longitude: Double, val label: String?)

    /** Last successful observation, kept across restarts. The caller decides whether it is fresh. */
    fun cached(): PanelWeatherSnapshot? =
        preferences.getString(KEY_SNAPSHOT, null)?.let { PanelWeatherSnapshot.parse(it) }

    suspend fun fetch(configuredCity: String): PanelWeatherSnapshot? = withContext(Dispatchers.IO) {
        val requestedCity = configuredCity.trim()
        val place = currentPlace(requestedCity)
        val snapshot = if (place == null) null else forecast(place)
        if (snapshot != null) {
            preferences.edit().putString(KEY_SNAPSHOT, snapshot.encode()).apply()
            return@withContext snapshot
        }
        cached() ?: PanelWeatherSnapshot(requestedCity, "unavailable", SOURCE)
    }

    private fun currentPlace(city: String): Place? {
        // An explicitly configured city wins; an empty city falls back to the device location.
        if (city.isNotBlank()) {
            for (name in listOf(city, city.removeSuffix("市")).distinct()) {
                geocode(name)?.let { return it }
            }
        }
        lastKnownLocation()?.let { return Place(it.latitude, it.longitude, null) }
        return null
    }

    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission(appContext)) return null
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return runCatching {
            manager.getProviders(true).orEmpty()
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    private fun geocode(name: String): Place? {
        val url = "$GEOCODING?name=${URLEncoder.encode(name, "UTF-8")}&count=1&language=zh"
        val body = fetchJson(url) ?: return null
        val place = body["results"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val latitude = place["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val longitude = place["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val label = listOfNotNull(
            place["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
            place["country"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
        ).joinToString(" / ").takeIf { it.isNotBlank() }
        return Place(latitude, longitude, label)
    }

    private fun forecast(place: Place): PanelWeatherSnapshot? {
        val latitude = String.format(Locale.ROOT, "%.6f", place.latitude)
        val longitude = String.format(Locale.ROOT, "%.6f", place.longitude)
        val url = "$FORECAST?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&temperature_unit=celsius&timeformat=unixtime&timezone=auto&forecast_days=3"
        val body = fetchJson(url) ?: return null
        val current = body["current"]?.jsonObject ?: return null
        val units = body["current_units"]?.jsonObject
        val daily = body["daily"]?.jsonObject ?: return null
        val dailyUnits = body["daily_units"]?.jsonObject
        if (units?.get("temperature_2m")?.jsonPrimitive?.content != "°C" ||
            units?.get("time")?.jsonPrimitive?.content != "unixtime" ||
            dailyUnits?.get("time")?.jsonPrimitive?.content != "unixtime" ||
            dailyUnits?.get("temperature_2m_min")?.jsonPrimitive?.content != "°C" ||
            dailyUnits?.get("temperature_2m_max")?.jsonPrimitive?.content != "°C" ||
            dailyUnits?.get("weather_code")?.jsonPrimitive?.content != "wmo code"
        ) return null

        val temperature = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: return null
        val code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: return null
        val observedAt = current["time"]?.jsonPrimitive?.longOrNull ?: return null
        val timezone = clean(body["timezone"]?.jsonPrimitive?.content ?: return null, 80).ifEmpty { "UTC" }
        val offset = body["utc_offset_seconds"]?.jsonPrimitive?.intOrNull ?: return null
        val fetchedAt = System.currentTimeMillis() / 1000
        val times = daily["time"]?.jsonArray ?: return null
        val codes = daily["weather_code"]?.jsonArray ?: return null
        val minimums = daily["temperature_2m_min"]?.jsonArray ?: return null
        val maximums = daily["temperature_2m_max"]?.jsonArray ?: return null
        val today = PanelWeatherSnapshot.dateAtOffset(fetchedAt + offset, 0)
        val firstDate = times.firstOrNull()?.jsonPrimitive?.longOrNull
            ?.let { PanelWeatherSnapshot.dateAtOffset(it + offset, 0) }
        val hasForecast = times.size == 3 && codes.size == 3 && minimums.size == 3 && maximums.size == 3 &&
            firstDate == today && offset in -43_200..50_400
        val days = if (hasForecast) {
            val items = mutableListOf<PanelForecastDay>()
            var complete = true
            for (index in 0 until 3) {
                val dayMinimum = minimums[index].jsonPrimitive.doubleOrNull
                val dayMaximum = maximums[index].jsonPrimitive.doubleOrNull
                val dayCode = codes[index].jsonPrimitive.intOrNull
                if (dayMinimum == null || dayMaximum == null || dayCode == null) {
                    complete = false
                    break
                }
                items += PanelForecastDay(
                    PanelWeatherSnapshot.dateAtOffset(fetchedAt + offset + index * 86_400L, 0),
                    dayMinimum, dayMaximum, dayCode,
                )
            }
            if (complete) items else emptyList()
        } else emptyList()

        val coordinates = String.format(Locale.ROOT, "%.2f, %.2f", place.latitude, place.longitude)
        val resolved = clean(place.label ?: resolvedCity(place, timezone), 260).ifBlank { coordinates }
        // Phone-side snapshots never enter a DIAL packet. Their `city` carries the locally resolved
        // place name, which keeps PanelWeatherSnapshot.parse's non-blank city rule satisfied even
        // when the shared configuration leaves the city unset.
        val cityKey = resolved.take(120)

        val payload = buildJsonObject {
            put("city", cityKey)
            put("status", "ready")
            put("source", SOURCE)
            put("resolvedCity", resolved)
            put("observedAt", observedAt)
            put("fetchedAt", fetchedAt)
            put("temperature", temperature)
            put("weatherCode", code)
            put("timezone", timezone)
            put("utcOffsetSeconds", offset)
            putJsonArray("forecast") {
                days.forEach { day ->
                    addJsonObject {
                        put("date", day.date)
                        put("minimum", day.minimum)
                        put("maximum", day.maximum)
                        put("weatherCode", day.weatherCode)
                    }
                }
            }
        }
        return PanelWeatherSnapshot.parse(payload.toString())
    }

    @Suppress("DEPRECATION")
    private fun resolvedCity(place: Place, timezone: String): String {
        val geocoded = if (Geocoder.isPresent()) {
            runCatching {
                Geocoder(appContext, Locale.getDefault()).getFromLocation(place.latitude, place.longitude, 1)
                    ?.firstOrNull()
                    ?.let { listOfNotNull(it.locality, it.adminArea, it.countryName).joinToString(" / ") }
            }.getOrNull()
        } else null
        val fromZone = timezone.substringAfterLast('/').replace('_', ' ')
        val coordinates = String.format(Locale.ROOT, "%.2f, %.2f", place.latitude, place.longitude)
        return clean(listOf(geocoded, fromZone, coordinates).firstOrNull { !it.isNullOrBlank() } ?: coordinates, 260)
    }

    private fun clean(value: String, maximum: Int): String = value.filterNot(Char::isISOControl).trim().take(maximum)

    private fun fetchJson(urlText: String): JsonObject? {
        val connection = URL(urlText).openConnection() as? HttpURLConnection ?: return null
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "KDEConnect-ControlPanel-Android/1 (personal non-commercial fork)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(4096)
                while (bytes.size() <= MAX_RESPONSE) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    bytes.write(chunk, 0, read)
                }
            }
            if (bytes.size() > MAX_RESPONSE) return null
            return JsonHelper.json.parseToJsonElement(bytes.toString("UTF-8")).jsonObject
        } catch (_: Exception) {
            return null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val SOURCE = "open-meteo"
        private const val PREFERENCES = "panel_local_weather"
        private const val KEY_SNAPSHOT = "snapshot"
        private const val MAX_RESPONSE = 65_536
        private const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"
        private const val FORECAST = "https://api.open-meteo.com/v1/forecast"

        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

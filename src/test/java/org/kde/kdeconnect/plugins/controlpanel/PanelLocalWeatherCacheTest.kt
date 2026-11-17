/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28, 37])
class PanelLocalWeatherCacheTest {
    @Test fun forecastDatesMustMatchEveryProviderDay() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val preferences = context.getSharedPreferences("panel_local_weather", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis() / 1000
        val geocode = Json.parseToJsonElement("""{"results":[{"latitude":39.9,"longitude":116.4,"name":"Beijing"}]}""").jsonObject
        fun forecast(offset: Int, midnight: Long, secondDay: Long) = Json.parseToJsonElement("""
            {"timezone":"Test/City","utc_offset_seconds":$offset,
             "current":{"temperature_2m":20,"weather_code":0,"time":$now},
             "current_units":{"temperature_2m":"°C","time":"unixtime"},
             "daily_units":{"time":"unixtime","temperature_2m_min":"°C","temperature_2m_max":"°C","weather_code":"wmo code"},
             "daily":{"time":[$midnight,$secondDay,${midnight + 172_800}],"weather_code":[0,1,2],
                      "temperature_2m_min":[10,11,12],"temperature_2m_max":[20,21,22]}}
        """).jsonObject
        try {
            for (offset in listOf(-43_200, -18_000, 0, 28_800, 50_400)) {
                val midnight = Math.floorDiv(now + offset, 86_400L) * 86_400L - offset
                val good = PanelLocalWeather(context) { url ->
                    if (url.contains("geocoding-api")) geocode else forecast(offset, midnight, midnight + 86_400)
                }
                assertEquals("offset=$offset", 3, checkNotNull(good.fetch("Beijing")).forecast.size)
                val bad = PanelLocalWeather(context) { url ->
                    if (url.contains("geocoding-api")) geocode else forecast(offset, midnight, midnight)
                }
                val result = checkNotNull(bad.fetch("Beijing"))
                assertEquals("ready", result.status)
                assertTrue("Repeated provider dates must not become a fabricated forecast (offset=$offset)", result.forecast.isEmpty())
            }
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun malformedProviderResponsesUseOnlyMatchingCityCache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val preferences = context.getSharedPreferences("panel_local_weather", Context.MODE_PRIVATE)
        val snapshot = PanelWeatherSnapshot("Beijing", "unavailable", "open-meteo")
        val badGeocode = Json.parseToJsonElement("""{"results": {}}""").jsonObject
        val validGeocode = Json.parseToJsonElement("""{"results":[{"latitude":39.9,"longitude":116.4,"name":"Beijing"}]}""").jsonObject
        val badForecast = Json.parseToJsonElement("""{"current": []}""").jsonObject
        try {
            preferences.edit().clear().putString("configured_city", "Beijing")
                .putString("snapshot", snapshot.encode()).commit()
            val brokenLocation = PanelLocalWeather(context) { badGeocode }
            assertEquals(snapshot, brokenLocation.fetch("Beijing"))
            assertEquals("unavailable", brokenLocation.fetch("Shanghai")?.status)
            assertEquals("Shanghai", brokenLocation.fetch("Shanghai")?.city)
            val brokenForecast = PanelLocalWeather(context) { url ->
                if (url.contains("geocoding-api")) validGeocode else badForecast
            }
            assertEquals(snapshot, brokenForecast.fetch("Beijing"))
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun cacheIsBoundToTheRequestedCityIncludingAutomaticLocation() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val preferences = context.getSharedPreferences("panel_local_weather", Context.MODE_PRIVATE)
        val snapshot = PanelWeatherSnapshot("Beijing", "unavailable", "open-meteo")
        try {
            preferences.edit().clear().putString("snapshot", snapshot.encode()).commit()
            val provider = PanelLocalWeather(context)
            assertNull(provider.cached("Beijing")) // Old caches do not identify their request.
            preferences.edit().putString("configured_city", "Beijing").commit()
            assertEquals(snapshot, provider.cached(" Beijing "))
            assertNull(provider.cached("Shanghai"))
            assertNull(provider.cached(""))
            preferences.edit().putString("configured_city", "").commit()
            assertEquals(snapshot, provider.cached(""))
            assertNull(provider.cached("Beijing"))
        } finally {
            preferences.edit().clear().commit()
        }
    }
}

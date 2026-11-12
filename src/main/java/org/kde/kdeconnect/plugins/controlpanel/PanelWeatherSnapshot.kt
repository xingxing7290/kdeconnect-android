/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.kde.kdeconnect.helpers.JsonHelper

@Serializable
data class PanelWeatherSnapshot(
    val city: String,
    val status: String,
    val source: String,
    val resolvedCity: String? = null,
    val observedAt: Long? = null,
    val fetchedAt: Long? = null,
    val temperature: Double? = null,
    val weatherCode: Int? = null,
) {
    val hasObservation: Boolean get() = observedAt != null && fetchedAt != null && temperature != null && weatherCode != null && resolvedCity != null
    fun fresh(now: Long): Boolean = hasObservation && now - checkNotNull(observedAt) in -600..10_800 && now - checkNotNull(fetchedAt) in -600..7200
    fun encode(): String = JsonHelper.json.encodeToString(this)
    fun withObservation(previous: PanelWeatherSnapshot): PanelWeatherSnapshot = copy(
        resolvedCity = previous.resolvedCity, observedAt = previous.observedAt, fetchedAt = previous.fetchedAt,
        temperature = previous.temperature, weatherCode = previous.weatherCode,
    )
    companion object {
        private val codes = setOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        fun parse(raw: String): PanelWeatherSnapshot? {
            return try {
                require(raw.length <= 16_384 && raw.toByteArray(Charsets.UTF_8).size <= 16_384)
                PanelConfigJson.requireSafeDepth(raw)
                val body = JsonHelper.json.parseToJsonElement(raw) as? JsonObject ?: return null
                fun text(key: String, maximum: Int, empty: Boolean = false): String? {
                    val value = (body[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                    return value.takeIf { it.length <= maximum && (empty || it.isNotBlank()) && it.none(Char::isISOControl) }
                }
                fun integer(key: String) = (body[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
                val city = text("city", 120, true) ?: return null
                val status = text("status", 20) ?: return null
                require(status in setOf("ready", "loading", "unavailable", "disabled", "no_city"))
                require(text("source", 30) == "open-meteo")
                val hasValues = listOf("resolvedCity", "observedAt", "fetchedAt", "temperature", "weatherCode").any { body[it] != null && body[it] != JsonNull }
                if (!hasValues) {
                    require(status != "ready")
                    PanelWeatherSnapshot(city, status, "open-meteo")
                } else {
                    require(city.isNotBlank())
                    val resolved = text("resolvedCity", 260) ?: return null
                    val observed = integer("observedAt") ?: return null
                    val fetched = integer("fetchedAt") ?: return null
                    val temperature = (body["temperature"] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull ?: return null
                    val code = integer("weatherCode") ?: return null
                    require(observed in 0..4_102_444_800 && fetched in 0..4_102_444_800 && observed <= fetched + 600)
                    require(temperature.isFinite() && temperature in -100.0..70.0 && (code in 0L..99L && code.toInt() in codes))
                    PanelWeatherSnapshot(city, status, "open-meteo", resolved, observed, fetched, temperature, code.toInt())
                }
            } catch (_: IllegalArgumentException) { null }
        }
    }
}

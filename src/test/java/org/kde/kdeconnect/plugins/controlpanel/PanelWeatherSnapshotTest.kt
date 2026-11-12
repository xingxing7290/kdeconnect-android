/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.kde.kdeconnect.helpers.JsonHelper

class PanelWeatherSnapshotTest {
    private val now = 1_800_000_000L
    private fun snapshot(status: String = "ready", fetched: Long = now) = PanelWeatherSnapshot("长沙市", status, "open-meteo", "长沙 / 中国", fetched - 300, fetched, 18.5, 3)
    private fun raw(value: PanelWeatherSnapshot = snapshot()) = value.encode()
    private fun change(field: String, value: JsonElement): String {
        val objectValue = JsonHelper.json.parseToJsonElement(raw()).jsonObject.toMutableMap()
        objectValue[field] = value
        return JsonObject(objectValue).toString()
    }
    private fun config(city: String = "长沙市") = PanelConfig.parse(File("docs/control-panel/fixtures/config-v1.json").readText()).let { it.copy(idle = it.idle.copy(city = city)) }
    private fun ready() = PanelSession().apply { connected(); assertTrue(receiveConfig(config().encode())) }
    @Test fun strictSnapshotRoundTripAndFreshness() {
        val value = checkNotNull(PanelWeatherSnapshot.parse(raw()))
        assertEquals(snapshot(), value)
        assertTrue(value.fresh(now))
        assertFalse(value.fresh(now + 7201))
        assertFalse(value.fresh(now - 601))
        assertNull(PanelWeatherSnapshot.parse(change("temperature", JsonPrimitive("18.5"))))
        assertNull(PanelWeatherSnapshot.parse(change("weatherCode", JsonPrimitive(4))))
        assertNull(PanelWeatherSnapshot.parse(change("weatherCode", JsonPrimitive(3.5))))
        assertNull(PanelWeatherSnapshot.parse(change("temperature", JsonPrimitive(71))))
        assertNull(PanelWeatherSnapshot.parse(change("observedAt", JsonPrimitive(now + 601))))
        assertNull(PanelWeatherSnapshot.parse(change("source", JsonPrimitive("unknown"))))
        assertNull(PanelWeatherSnapshot.parse("[".repeat(65) + "]".repeat(65)))
        assertNull(PanelWeatherSnapshot.parse("x".repeat(16_385)))
    }
    @Test fun revisionCityAndOutOfOrderDataCannotReplaceCurrentWeather() {
        val session = ready()
        assertFalse(session.receiveWeather(6, raw(), now))
        assertTrue(session.receiveWeather(7, raw(), now))
        assertFalse(session.receiveWeather(7, raw(snapshot(fetched = now - 600)), now))
        assertFalse(session.receiveWeather(7, raw(snapshot().copy(fetchedAt = now + 10, observedAt = now - 600)), now))
        assertEquals(snapshot(), session.state.value.weather)
        assertFalse(session.receiveWeather(7, raw(snapshot().copy(city = "Other")), now))
        assertFalse(session.receiveWeather(7, raw(snapshot(fetched = now + 601)), now))
        assertEquals(PanelConnection.READY, session.state.value.connection)
    }
    @Test fun failuresKeepCachedObservationButNeverInventFetchTime() {
        val session = ready()
        assertTrue(session.receiveWeather(7, raw(), now))
        val failure = PanelWeatherSnapshot("长沙市", "unavailable", "open-meteo")
        assertTrue(session.receiveWeather(7, raw(failure), now))
        val cached = checkNotNull(session.state.value.weather)
        assertEquals("unavailable", cached.status)
        assertEquals(now, cached.fetchedAt)
        assertEquals(18.5, checkNotNull(cached.temperature), 0.001)
        assertTrue(cached.fresh(now))
        assertFalse(cached.fresh(now + 7201))
        assertEquals(PanelConnection.READY, session.state.value.connection)
    }
    @Test fun offlineRestoreIsPerCityAndLimitedToOneDay() {
        val session = PanelSession()
        session.restoreDisplay(config().encode())
        session.restoreWeather(raw(), now)
        assertEquals(snapshot(), session.state.value.weather)
        session.restoreDisplay(config("Other").encode())
        assertNull(session.state.value.weather)
        session.restoreWeather(raw(), now)
        assertNull(session.state.value.weather)
        val expired = PanelSession()
        expired.restoreDisplay(config().encode())
        expired.restoreWeather(raw(snapshot(fetched = now - 86_401)), now)
        assertNull(expired.state.value.weather)
        assertFalse(expired.receiveWeather(7, raw(), now))
    }
    @Test fun configurationCityChangesClearWeatherImmediately() {
        val session = ready()
        assertTrue(session.receiveWeather(7, raw(), now))
        assertTrue(session.receiveConfig(config("Other").copy(revision = 8).encode()))
        assertNull(session.state.value.weather)
        assertFalse(session.receiveWeather(7, raw(), now))
        assertEquals(PanelConnection.READY, session.state.value.connection)
    }
}

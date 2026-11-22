/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Plain JVM checks for the smart-home snapshot. No Android runtime, no network. */
class PanelHomeAssistantTest {
    private val now = 1_763_200_010L

    private fun entities(): String = """
        [
          {"id":"switch.kitchen","type":"switch","name":"厨房灯","state":"on","group":"一楼","order":0},
          {"id":"light.living","type":"light","name":"客厅灯","state":"off","brightness":128,"order":1},
          {"id":"sensor.temp","type":"sensor","name":"温度","state":"23.5","unit":"°C","order":2}
        ]
    """.trimIndent()

    private fun snapshot(
        status: String = "ready",
        error: String = "",
        fetchedAt: Long = now - 5,
        entities: String = entities(),
        extra: String = "",
    ): String = buildString {
        append("{")
        append("\"status\":\"$status\",")
        append("\"error\":\"$error\",")
        append("\"fetchedAt\":$fetchedAt,")
        append("\"entities\":$entities")
        if (extra.isNotEmpty()) append(",$extra")
        append("}")
    }

    @Test fun sharedFixtureIsWireCompatible() {
        val raw = File("docs/control-panel/fixtures/hass-snapshot.json").readText()
        val parsed = checkNotNull(PanelHassState.parse(raw))
        assertEquals(3, parsed.entities.size)
        assertEquals("厨房主灯", parsed.entities[0].name)
        assertEquals(128, parsed.entities[1].brightness)
        assertEquals("°C", parsed.entities[2].unit)
        assertEquals("switch.kitchen", parsed.lastCommandEntity)
        assertTrue(parsed.fresh(now, 45_000))
    }

    @Test fun validSnapshotRoundTrips() {
        val parsed = checkNotNull(PanelHassState.parse(snapshot()))
        assertEquals(3, parsed.entities.size)
        assertEquals("厨房灯", parsed.entities[0].name)
        assertEquals("一楼", parsed.entities[0].group)
        assertTrue(parsed.entities[0].isOn)
        assertEquals(128, parsed.entities[1].brightness)
        assertEquals("°C", parsed.entities[2].unit)
        assertNull(parsed.lastCommandEntity)
        assertTrue(parsed.fresh(now, 45_000))
    }

    @Test fun entityCapabilitiesAndAvailability() {
        val parsed = checkNotNull(PanelHassState.parse(snapshot()))
        assertTrue(parsed.entities[0].controllable)
        assertTrue(parsed.entities[1].controllable)
        assertFalse(parsed.entities[2].controllable) // sensor is read-only
        assertTrue(parsed.entities[0].available)

        val unavailable = """
            [{"id":"switch.kitchen","type":"switch","name":"厨房灯","state":"unavailable"}]
        """.trimIndent()
        val offline = checkNotNull(PanelHassState.parse(snapshot(entities = unavailable)))
        assertFalse(offline.entities[0].available)
    }

    @Test fun malformedEntityIsSkippedWithoutDroppingTheSnapshot() {
        val mixed = """
            [
              {"id":"switch.kitchen","type":"switch","name":"厨房灯","state":"on"},
              {"id":"BAD.ID","type":"switch","name":"非法实体","state":"on"},
              {"id":"light.living","type":"light","name":"客厅灯","state":"off"},
              {"id":"switch.kitchen","type":"switch","name":"重复","state":"off"}
            ]
        """.trimIndent()
        val parsed = checkNotNull(PanelHassState.parse(snapshot(entities = mixed)))
        assertEquals(2, parsed.entities.size) // bad id and duplicate both skipped
        assertEquals("厨房灯", parsed.entities[0].name)
    }

    @Test fun hostileFieldsDropOnlyTheirEntity() {
        val tooLongName = """
            [
              {"id":"switch.kitchen","type":"switch","name":"${"x".repeat(121)}","state":"on"},
              {"id":"light.living","type":"light","name":"客厅灯","state":"off"}
            ]
        """.trimIndent()
        assertEquals(1, checkNotNull(PanelHassState.parse(snapshot(entities = tooLongName))).entities.size)

        val controlCharacter = """
            [{"id":"switch.kitchen","type":"switch","name":"line\u0001break","state":"on"}]
        """.trimIndent()
        assertEquals(0, checkNotNull(PanelHassState.parse(snapshot(entities = controlCharacter))).entities.size)

        val badBrightness = """
            [{"id":"light.living","type":"light","name":"客厅灯","state":"on","brightness":256}]
        """.trimIndent()
        assertEquals(0, checkNotNull(PanelHassState.parse(snapshot(entities = badBrightness))).entities.size)
    }

    @Test fun invalidEnvelopeIsRejected() {
        assertNull(PanelHassState.parse(snapshot(status = "flying")))
        assertNull(PanelHassState.parse(snapshot(fetchedAt = 4_102_444_801L)))
        assertNull(PanelHassState.parse("[]"))
        assertNull(PanelHassState.parse("{oops"))
        assertNull(PanelHassState.parse("x".repeat(65_537)))

        // An unusable error string is cosmetic: degrade it instead of discarding the readings.
        val parsed = checkNotNull(PanelHassState.parse(snapshot(status = "error", error = "x".repeat(161))))
        assertEquals("", parsed.error)
        assertFalse(parsed.fresh(now, 45_000))
    }

    @Test fun commandFeedbackIsAdvisory() {
        val withFeedback = snapshot(
            extra = "\"lastCommand\":{\"entityId\":\"switch.kitchen\",\"error\":\"hass_unknown_action\"}",
        )
        val parsed = checkNotNull(PanelHassState.parse(withFeedback))
        assertEquals("switch.kitchen", parsed.lastCommandEntity)
        assertEquals("hass_unknown_action", parsed.lastCommandError)
        assertEquals(3, parsed.entities.size)

        val malformed = snapshot(extra = "\"lastCommand\":{\"entityId\":12345,\"error\":{}}")
        val stillParsed = checkNotNull(PanelHassState.parse(malformed))
        assertEquals(3, stillParsed.entities.size)
        assertNull(stillParsed.lastCommandEntity)
        assertNull(stillParsed.lastCommandError)
    }

    @Test fun freshnessIsBoundToReadyStatusAndFetchedAt() {
        val ready = checkNotNull(PanelHassState.parse(snapshot(fetchedAt = now - 60)))
        assertTrue(ready.fresh(now, 45_000))
        val old = checkNotNull(PanelHassState.parse(snapshot(fetchedAt = now - 120)))
        assertFalse(old.fresh(now, 45_000))
        val future = checkNotNull(PanelHassState.parse(snapshot(fetchedAt = now + 30)))
        assertTrue(future.fresh(now, 45_000)) // bounded clock skew is tolerated
        val error = checkNotNull(PanelHassState.parse(snapshot(status = "error", error = "hass_offline")))
        assertFalse(error.fresh(now, 45_000))
        assertNotNull(error.entities)
    }
}

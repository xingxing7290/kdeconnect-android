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

/** Plain JVM checks for the desktop-hardware snapshot. No Android runtime, no network. */
class PanelSystemInfoTest {
    private val now = 1_763_200_010L

    private fun raw(): String = """
        {
          "protocolVersion": 1, "revision": 7, "status": "ready", "error": "",
          "fetchedAt": 1763200005,
          "cores": 8,
          "cpu": {"usage": 23.5},
          "memory": {"usedBytes": 8589934592, "totalBytes": 17179869184, "usage": 50.0},
          "disk": {"usedBytes": 214748364800, "totalBytes": 536870912000, "usage": 40.0},
          "network": {"rxBytesPerSec": 12800, "txBytesPerSec": 3200},
          "uptimeSeconds": 345600, "processCount": 214
        }
    """.trimIndent()

    @Test fun sharedFixtureIsWireCompatible() {
        val parsed = checkNotNull(PanelSystemInfo.parse(File("docs/control-panel/fixtures/sysinfo-snapshot.json").readText()))
        assertEquals(8, parsed.cores)
        assertEquals(23.5, parsed.cpuUsage!!, 0.01)
        assertEquals(50.0, parsed.memoryUsage!!, 0.01)
        assertEquals(40.0, parsed.diskUsage!!, 0.01)
        assertEquals(12800L, parsed.rxBytesPerSec)
        assertEquals(3200L, parsed.txBytesPerSec)
        assertEquals(345600L, parsed.uptimeSeconds)
        assertEquals(214, parsed.processCount)
        assertTrue(parsed.fresh(now, 20_000))
    }

    @Test fun validSnapshotRoundTrips() {
        val parsed = checkNotNull(PanelSystemInfo.parse(raw()))
        assertEquals("ready", parsed.status)
        assertEquals("23.5%", parsed.cpuText)
        assertEquals("50.0%", parsed.memoryText)
        assertEquals("40.0%", parsed.diskText)
        assertEquals("8.0 GB", PanelSystemInfo.formatBytes(parsed.memoryUsed))
        assertEquals("12.5 KB/s", PanelSystemInfo.formatRate(parsed.rxBytesPerSec))
        assertEquals("4d 0h 0m", PanelSystemInfo.formatUptime(parsed.uptimeSeconds))
    }

    @Test fun invalidSectionsAreDroppedIndependently() {
        val bad = raw()
            .replace("\"usage\": 23.5", "\"usage\": 150")
            .replace("\"totalBytes\": 17179869184", "\"totalBytes\": 1")
            .replace("\"uptimeSeconds\": 345600", "\"uptimeSeconds\": -5")
            .replace("\"processCount\": 214", "\"processCount\": 200000")
        val parsed = checkNotNull(PanelSystemInfo.parse(bad))
        assertNull(parsed.cpuUsage)
        assertNull(parsed.memoryUsage) // used > total now
        assertNull(parsed.uptimeSeconds)
        assertNull(parsed.processCount)
        // Valid sections survive.
        assertNotNull(parsed.diskUsage)
        assertNotNull(parsed.rxBytesPerSec)
    }

    @Test fun invalidEnvelopeIsRejected() {
        assertNull(PanelSystemInfo.parse(raw().replace("\"status\": \"ready\"", "\"status\": \"flying\"")))
        assertNull(PanelSystemInfo.parse(raw().replace("\"fetchedAt\": 1763200005", "\"fetchedAt\": 4102444801")))
        assertNull(PanelSystemInfo.parse("[]"))
        assertNull(PanelSystemInfo.parse("{oops"))
        assertNull(PanelSystemInfo.parse("x".repeat(16_385)))
    }

    @Test fun freshnessRequiresReadyAndRecentFetchedAt() {
        val ready = checkNotNull(PanelSystemInfo.parse(raw()))
        assertTrue(ready.fresh(now, 20_000))
        assertFalse(ready.fresh(now + 60_000, 20_000))
        val error = checkNotNull(PanelSystemInfo.parse(raw().replace("\"status\": \"ready\"", "\"status\": \"error\"")))
        assertFalse(error.fresh(now, 20_000))
        val disabled = checkNotNull(PanelSystemInfo.parse(raw().replace("\"status\": \"ready\"", "\"status\": \"disabled\"")))
        assertFalse(disabled.fresh(now, 20_000))
    }

    @Test fun absentOptionalFieldsAreNull() {
        val minimal = """{"status":"ready","error":"","fetchedAt":1763200005,"cpu":{}}""".trimIndent()
        val parsed = checkNotNull(PanelSystemInfo.parse(minimal))
        assertNull(parsed.cores)
        assertNull(parsed.memoryUsage)
        assertNull(parsed.rxBytesPerSec)
        assertNull(parsed.txBytesPerSec)
        assertEquals("--", parsed.cpuText)
        assertEquals("--", PanelSystemInfo.formatBytes(null))
        assertEquals("--", PanelSystemInfo.formatRate(null))
        assertEquals("--", PanelSystemInfo.formatUptime(null))
    }
}

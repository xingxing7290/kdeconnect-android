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

/** Plain JVM checks for the live progress packet. No Android runtime, no network. */
class PanelWorkProgressTest {
    private val now = 1_763_200_010L

    private fun sample(): PanelWorkProgress = PanelWorkProgress(
        "workbuddy", "running", "整理季度报告", "生成图表", "已完成 3/8 个文件", 38, 7,
        now - 5, now - 3)

    private fun json(vararg overrides: Pair<String, String>): String {
        val values = mutableMapOf(
            "source" to "\"workbuddy\"",
            "status" to "\"running\"",
            "title" to "\"整理季度报告\"",
            "stage" to "\"生成图表\"",
            "detail" to "\"已完成 3/8 个文件\"",
            "percent" to "38",
            "sequence" to "7",
            "updatedAt" to (now - 5).toString(),
            "fetchedAt" to (now - 3).toString(),
        )
        overrides.forEach { (key, value) -> if (value.isEmpty()) values.remove(key) else values[key] = value }
        return values.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
    }

    @Test fun validSnapshotRoundTrips() {
        val value = sample()
        assertEquals(value, PanelWorkProgress.parse(value.encode()))
        val parsed = checkNotNull(PanelWorkProgress.parse(json()))
        assertEquals(38, parsed.percent)
        assertEquals("生成图表", parsed.stage)
    }

    @Test fun optionalFieldsAreOptionalButNeverCoerced() {
        assertNotNull(PanelWorkProgress.parse(json("percent" to "")))
        assertNull(PanelWorkProgress.parse(json("percent" to "\"38\"")))
        assertNull(PanelWorkProgress.parse(json("percent" to "101")))
        assertNull(PanelWorkProgress.parse(json("percent" to "-1")))
        assertNull(PanelWorkProgress.parse(json("sequence" to "\"7\"")))
        assertNotNull(PanelWorkProgress.parse(json("title" to "\"\"", "stage" to "\"\"", "detail" to "\"\"")))
    }

    @Test fun invalidOrHostilePacketsAreRejected() {
        val invalid = listOf(
            json("source" to "\"other\""),
            json("status" to "\"flying\""),
            json("status" to "1"),
            json("title" to "12"),
            json("detail" to "\"line\u0001break\""),
            json("title" to "\"${"x".repeat(121)}\""),
            json("detail" to "\"${"x".repeat(241)}\""),
            json("sequence" to "-1"),
            json("sequence" to "9007199254740992"),
            json("updatedAt" to "\"123\""),
            json("fetchedAt" to "1.5"),
            json("fetchedAt" to "0"),
            json("updatedAt" to "0", "fetchedAt" to "5000000000"),
            json("source" to ""),
            "not json",
            "[]",
            "x".repeat(16_385),
        )
        invalid.forEach { raw ->
            assertNull("Must reject: ${raw.take(90)}", PanelWorkProgress.parse(raw))
        }
    }

    @Test fun freshnessTracksTheTransportAndNeverAcceptsUnavailable() {
        val value = sample()
        assertTrue(value.fresh(now, 15_000))
        assertFalse("No heartbeat for 30 s must look stale", value.fresh(now + 30, 15_000))
        assertTrue(value.fresh(now + 30, 60_000))
        assertTrue("A static source timestamp must not invalidate a live link",
            value.copy(updatedAt = now - 86_400, fetchedAt = now).fresh(now, 15_000))
        assertFalse(value.copy(status = "unavailable").fresh(now, 120_000))
        assertFalse(value.copy(fetchedAt = now + 1_000).fresh(now, 120_000))
    }

    @Test fun ageIsClampedAndCountsFromTheSourceTimestamp() {
        assertEquals(5L, sample().ageSeconds(now))
        assertEquals(0L, sample().ageSeconds(now - 100))
        assertEquals(35L, sample().ageSeconds(now + 30))
    }
}

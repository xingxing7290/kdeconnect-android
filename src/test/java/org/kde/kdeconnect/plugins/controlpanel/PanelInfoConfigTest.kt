/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The right-column configuration must stay backwards compatible with older desktop configs. */
class PanelInfoConfigTest {
    private fun fixture(): String = File("docs/control-panel/fixtures/config-v1.json").readText()

    @Test
    fun sharedFixtureExposesTheRightColumnConfiguration() {
        val config = PanelConfig.parse(fixture())
        assertEquals(listOf("今天也要保持专注", "18:00 前提交周报"), config.idle.info.notes)
        assertTrue(config.idle.info.showForecast)
        assertFalse(config.idle.info.showProgress)
        assertEquals(15_000, config.idle.info.staleMs)
    }

    @Test
    fun configWithoutInfoFallsBackToLegacyNotesAndForecast() {
        val legacy = fixture().replace(Regex(",\\s*\"info\"\\s*:\\s*\\{[^}]*\\}"), "")
        assertTrue("The fixture must actually contain an info object", legacy.length < fixture().length)
        val config = PanelConfig.parse(legacy)
        assertEquals(PanelInfo(), config.idle.info)
        assertTrue("Legacy dials keep showing the three-day forecast", config.idle.info.showForecast)
        assertFalse("Legacy dials never show a progress card", config.idle.info.showProgress)
        assertEquals("今天也要保持专注", config.idle.message)
    }

    @Test
    fun invalidRightColumnConfigurationIsRejectedAsAWhole() {
        val cases = listOf(
            """{"notes":["1","2","3","4","5"],"showForecast":true,"showProgress":false,"staleMs":15000}""",
            """{"notes":["${"x".repeat(121)}"],"showForecast":true,"showProgress":false,"staleMs":15000}""",
            """{"notes":[],"showForecast":"yes","showProgress":false,"staleMs":15000}""",
            """{"notes":[],"showForecast":true,"showProgress":false,"staleMs":100}""",
            """{"notes":[],"showForecast":true,"showProgress":false,"staleMs":120001}""",
            """{"notes":[],"showForecast":true,"showProgress":false,"staleMs":"15000"}""",
            """{"notes":"not an array","showForecast":true,"showProgress":false,"staleMs":15000}""",
        )
        val pattern = Regex("\"info\"\\s*:\\s*\\{[^}]*\\}")
        cases.forEach { info ->
            val raw = fixture().replace(pattern, "\"info\": $info")
            assertThrows("Must reject ${info.take(80)}", IllegalArgumentException::class.java) {
                PanelConfig.parse(raw)
            }
        }
    }
}

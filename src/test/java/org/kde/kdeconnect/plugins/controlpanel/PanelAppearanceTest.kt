/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM checks: no Android runtime, no Compose rendering, no network. */
class PanelAppearanceTest {

    @Test
    fun storedValuesRoundTripAndUnknownValuesFallBackToSystem() {
        for (mode in PanelAppearanceMode.values()) {
            assertEquals(mode, PanelAppearanceMode.fromStored(mode.stored))
        }
        assertEquals(PanelAppearanceMode.SYSTEM, PanelAppearanceMode.fromStored(null))
        assertEquals(PanelAppearanceMode.SYSTEM, PanelAppearanceMode.fromStored(""))
        assertEquals(PanelAppearanceMode.SYSTEM, PanelAppearanceMode.fromStored("LIGHT"))
        assertEquals(PanelAppearanceMode.SYSTEM, PanelAppearanceMode.fromStored("deleted_theme"))
        assertEquals(listOf("system", "light", "dark"), PanelAppearanceMode.values().map { it.stored })
    }

    @Test
    fun onlyExplicitModesOverrideTheMaterialTheme() {
        assertNull(PanelAppearanceMode.SYSTEM.darkOverride)
        assertFalse(PanelAppearanceMode.LIGHT.darkOverride == true)
        assertTrue(PanelAppearanceMode.DARK.darkOverride == true)
    }

    @Test
    fun controlPalettesAreDistinctAndSystemFollowsTheDevice() {
        val light = controlPalette(PanelAppearanceMode.LIGHT)
        val dark = controlPalette(PanelAppearanceMode.DARK)
        assertEquals(Color(0xFFF4F6FA), light.background)
        assertEquals(Color(0xFF101620), dark.background)
        assertTrue("Dark surface must be darker than light surface", dark.surface.luminance() < light.surface.luminance())
        assertEquals(light.background, controlPalette(PanelAppearanceMode.SYSTEM, false).background)
        assertEquals(dark.background, controlPalette(PanelAppearanceMode.SYSTEM, true).background)
        assertEquals(light.background, controlPalette(PanelAppearanceMode.LIGHT, true).background)
        assertEquals(dark.background, controlPalette(PanelAppearanceMode.DARK, false).background)
    }

    @Test
    fun everyControlPanelTextColourIsReadableInBothModes() {
        for ((mode, palette) in listOf(PanelAppearanceMode.LIGHT to controlPalette(PanelAppearanceMode.LIGHT),
            PanelAppearanceMode.DARK to controlPalette(PanelAppearanceMode.DARK))) {
            for ((name, color) in listOf("text" to palette.text, "muted" to palette.textMuted,
                "accent" to palette.accent, "danger" to palette.danger, "success" to palette.success)) {
                assertTrue("$mode/$name contrast ${panelContrast(color, palette.background)}",
                    panelContrast(color, palette.background) >= 4.5)
            }
            assertTrue("$mode slot label", panelContrast(palette.text, palette.surface) >= 4.5)
        }
    }

    @Test
    fun dialTextColoursStayReadableInBothModes() {
        for (mode in listOf(PanelAppearanceMode.LIGHT, PanelAppearanceMode.DARK)) {
            val dial = dialPalette(mode, "digital")
            for ((name, color) in listOf("ink" to dial.ink, "quiet" to dial.quiet, "accent" to dial.accent,
                "offline" to dial.offline, "hint" to dial.hint)) {
                val ratio = panelContrast(color, dial.background)
                assertTrue("$mode/$name contrast $ratio is below 4.5", ratio >= 4.5)
            }
        }
    }

    @Test
    fun explicitModesApplyToEveryDialWhileSystemKeepsHistoricalBehaviour() {
        val dials = listOf("digital", "analog", "weather", "info", "car")
        dials.forEach { dialId ->
            assertTrue(dialPalette(PanelAppearanceMode.LIGHT, dialId).light)
            assertEquals(Color(0xFFECEEE8), dialPalette(PanelAppearanceMode.LIGHT, dialId).background)
            assertFalse(dialPalette(PanelAppearanceMode.DARK, dialId).light)
            assertEquals(Color.Black, dialPalette(PanelAppearanceMode.DARK, dialId).background)
        }
        // SYSTEM must not change a single pixel of the shipped dials.
        val info = dialPalette(PanelAppearanceMode.SYSTEM, "info")
        assertTrue(info.light)
        assertEquals(Color(0xFFECEEE8), info.background)
        assertEquals(Color(0xFF202722), info.ink)
        assertEquals(Color(0xFF5F655F), info.quiet)
        assertEquals(info.ink, info.accent)
        for (dialId in dials - "info") {
            val dark = dialPalette(PanelAppearanceMode.SYSTEM, dialId)
            assertFalse(dark.light)
            assertEquals(Color.Black, dark.background)
            assertEquals(Color(0xFFF5F7FA), dark.ink)
            assertEquals(Color(0xFF85B4FF), dark.accent)
        }
    }

    @Test
    fun contrastHelperMatchesWcagReferenceValues() {
        assertEquals(21.0, panelContrast(Color.Black, Color.White), 0.000001)
        assertEquals(1.0, panelContrast(Color(0xFF777777), Color(0xFF777777)), 0.000001)
        assertTrue(panelContrast(Color(0xFF777777), Color.White) < 4.5)
        assertTrue(isDarkBackground(Color(0xFF101620)))
        assertFalse(isDarkBackground(Color(0xFFF4F6FA)))
    }
}

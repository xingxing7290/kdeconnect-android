/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Phone-side appearance of the control panel and idle dial.
 *
 * SYSTEM keeps the historical behaviour: the desktop-supplied `appearance.background` is drawn
 * verbatim and only the "info" dial uses a light surface. LIGHT/DARK override that background
 * locally and recolour both screens. The choice is a local phone preference; it is deliberately
 * NOT part of panel.json, so switching it never changes the synced config revision.
 */
enum class PanelAppearanceMode(val stored: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    /** Forces a Material 3 colour scheme for this screen, or null to keep the app-wide setting. */
    val darkOverride: Boolean?
        get() = when (this) {
            SYSTEM -> null
            LIGHT -> false
            DARK -> true
        }

    companion object {
        fun fromStored(value: String?): PanelAppearanceMode =
            values().firstOrNull { it.stored == value } ?: SYSTEM
    }
}

/** Stored in a private preference file so it cannot collide with plugin or protocol settings. */
object PanelAppearanceStore {
    private const val FILE = "control_panel_appearance"
    private const val KEY = "mode"

    fun read(context: Context): PanelAppearanceMode = runCatching {
        PanelAppearanceMode.fromStored(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null))
    }.getOrDefault(PanelAppearanceMode.SYSTEM)

    fun write(context: Context, mode: PanelAppearanceMode) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putString(KEY, mode.stored).apply()
        }
    }
}

/** Colours for the 3x6 button grid. SYSTEM uses the legacy hardcoded palette in the screen. */
data class PanelPalette(
    val background: Color,
    val surface: Color,
    val border: Color,
    val empty: Color,
    val icon: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val danger: Color,
    val success: Color,
    val sliderTrack: Color,
)

private val LIGHT_PANEL = PanelPalette(
    background = Color(0xFFF4F6FA),
    surface = Color(0xFFFBFCFE),
    border = Color(0xFFDCE1E8),
    empty = Color(0xFF9BA5B4),
    icon = Color(0xFF4177C3),
    text = Color(0xFF25334A),
    textMuted = Color(0xFF5B6B80),
    accent = Color(0xFF1F5FC8),
    danger = Color(0xFFB3261E),
    success = Color(0xFF2E7D32),
    sliderTrack = Color(0xFFDCE5F5),
)

private val DARK_PANEL = PanelPalette(
    background = Color(0xFF101620),
    surface = Color(0xFF1C2533),
    border = Color(0xFF334155),
    empty = Color(0xFF64748B),
    icon = Color(0xFF8FB9FF),
    text = Color(0xFFF1F5FA),
    textMuted = Color(0xFFA8B7CC),
    accent = Color(0xFF85B4FF),
    danger = Color(0xFFFF9B93),
    success = Color(0xFF8BD49A),
    sliderTrack = Color(0xFF334155),
)

fun controlPalette(mode: PanelAppearanceMode, systemDark: Boolean = false): PanelPalette = when (mode) {
    PanelAppearanceMode.LIGHT -> LIGHT_PANEL
    PanelAppearanceMode.DARK -> DARK_PANEL
    PanelAppearanceMode.SYSTEM -> if (systemDark) DARK_PANEL else LIGHT_PANEL
}

/** Colours for the full-screen idle dial. */
data class PanelDialPalette(
    val light: Boolean,
    val background: Color,
    val ink: Color,
    val quiet: Color,
    val accent: Color,
    val offline: Color,
    val hint: Color,
    val tickMajor: Color,
    val tickMinor: Color,
    val hand: Color,
    val secondHand: Color,
    val carBody: Color,
    val carGlass: Color,
    val carWheel: Color,
    val carRim: Color,
    val carGround: Color,
)

// Same values the dials used before appearance modes existed.
private val LEGACY_DARK_DIAL = PanelDialPalette(
    light = false,
    background = Color.Black,
    ink = Color(0xFFF5F7FA),
    quiet = Color(0xFF8391A5),
    accent = Color(0xFF85B4FF),
    offline = Color(0xFFC06B66),
    hint = Color(0xFF8391A5),
    tickMajor = Color(0xFFD7DFE7),
    tickMinor = Color(0xFF5E6671),
    hand = Color.White,
    secondHand = Color(0xFFDBA353),
    carBody = Color(0xFF263D56),
    carGlass = Color(0xFF94C3ED),
    carWheel = Color(0xFF111720),
    carRim = Color(0xFF8AB9E8),
    carGround = Color(0xFF5B9ACF),
)

private val LEGACY_LIGHT_DIAL = PanelDialPalette(
    light = true,
    background = Color(0xFFECEEE8),
    ink = Color(0xFF202722),
    quiet = Color(0xFF5F655F),
    accent = Color(0xFF202722),
    offline = Color(0xFF9B403A),
    hint = Color(0xFF626C62),
    tickMajor = Color(0xFFD7DFE7),
    tickMinor = Color(0xFF5E6671),
    hand = Color.White,
    secondHand = Color(0xFFDBA353),
    carBody = Color(0xFF263D56),
    carGlass = Color(0xFF94C3ED),
    carWheel = Color(0xFF111720),
    carRim = Color(0xFF8AB9E8),
    carGround = Color(0xFF5B9ACF),
)

/** Every dial gets a light surface in LIGHT mode, and a black (AMOLED friendly) one in DARK mode. */
private val LIGHT_DIAL = LEGACY_LIGHT_DIAL.copy(
    quiet = Color(0xFF626C62),
    accent = Color(0xFF1F4FA8),
    tickMajor = Color(0xFF3E4A46),
    tickMinor = Color(0xFF8A9690),
    hand = Color(0xFF202722),
    secondHand = Color(0xFFA45B12),
    carRim = Color(0xFF3E6F9C),
)

fun dialPalette(mode: PanelAppearanceMode, dialId: String): PanelDialPalette = when (mode) {
    PanelAppearanceMode.LIGHT -> LIGHT_DIAL
    PanelAppearanceMode.DARK -> LEGACY_DARK_DIAL
    PanelAppearanceMode.SYSTEM -> if (dialId == "info") LEGACY_LIGHT_DIAL else LEGACY_DARK_DIAL
}

/** WCAG relative luminance, implemented locally so it can be checked in plain JVM unit tests. */
internal fun Color.luminance(): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

internal fun panelContrast(foreground: Color, background: Color): Double {
    val a = foreground.luminance()
    val b = background.luminance()
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

internal fun isDarkBackground(color: Color): Boolean = color.luminance() < 0.18

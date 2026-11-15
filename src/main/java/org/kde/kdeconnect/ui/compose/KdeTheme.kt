/*
 * SPDX-FileCopyrightText: 2024 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.ui.ThemeUtil

/**
 * App-wide Material 3 theme.
 *
 * The settings screen already offers "system default / light / dark" through `theme_pref`.
 * AppCompatDelegate applies that choice to classic views, but Compose reads only the
 * configuration, so the preference is resolved here as well. [darkTheme] lets a screen force
 * a mode without touching the global preference (used by the control panel appearance switch).
 */
@Composable
fun KdeTheme(context: Context, darkTheme: Boolean? = null, content: @Composable () -> Unit) {
    val preferred = remember(context) { readThemePreference(context) }
    val dark = darkTheme ?: when (preferred) {
        ThemeUtil.LIGHT_MODE -> false
        ThemeUtil.DARK_MODE -> true
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/** Previews and unit tests may not have a real preference store; fall back to the system theme. */
private fun readThemePreference(context: Context): String = runCatching {
    PreferenceManager.getDefaultSharedPreferences(context)
        .getString(ThemeUtil.KEY_THEME_PREF, ThemeUtil.DEFAULT_MODE) ?: ThemeUtil.DEFAULT_MODE
}.getOrDefault(ThemeUtil.DEFAULT_MODE)

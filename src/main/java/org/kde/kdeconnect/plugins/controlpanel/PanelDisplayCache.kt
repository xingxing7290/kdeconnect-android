/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.content.Context
import android.content.SharedPreferences
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.plugins.Plugin

internal object PanelDisplayCache {
    fun restore(preferences: SharedPreferences, session: PanelSession) {
        string(preferences, "panel_config")?.let(session::restoreDisplay)
        string(preferences, "panel_weather")?.let { session.restoreWeather(it) }
    }

    fun offline(context: Context, device: Device): PanelSession? {
        if (!device.isPaired) return null
        val key = Plugin.getPluginKey(ControlPanelPlugin::class.java)
        val preferences = context.getSharedPreferences("${device.deviceId}_${key}_preferences", Context.MODE_PRIVATE)
        return PanelSession().also { restore(preferences, it) }
    }

    private fun string(preferences: SharedPreferences, key: String): String? = try {
        preferences.getString(key, null)
    } catch (_: ClassCastException) { null }
}

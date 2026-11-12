/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

data class PanelBatteryState(val percent: Int? = null, val charging: Boolean = false) {
    companion object {
        internal fun from(intent: Intent?): PanelBatteryState {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return PanelBatteryState()
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (scale <= 0 || level !in 0..scale) return PanelBatteryState()
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            return PanelBatteryState((100.0 * level / scale).roundToInt(), status in setOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL))
        }
    }
}

@Composable
internal fun rememberPanelBatteryState(): PanelBatteryState {
    val context = LocalContext.current.applicationContext
    var state by remember(context) { mutableStateOf(PanelBatteryState()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { state = PanelBatteryState.from(intent) }
        }
        var registered = false
        try {
            // This filter contains only a protected system broadcast (Android's flag exception).
            val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            registered = true
            state = PanelBatteryState.from(sticky)
        } catch (_: SecurityException) {
            state = PanelBatteryState()
        }
        onDispose {
            if (registered) context.unregisterReceiver(receiver)
        }
    }
    return state
}

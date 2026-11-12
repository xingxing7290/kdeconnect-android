/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 28, 37])
class PanelBatteryStateTest {
    private fun battery(level: Int, scale: Int, status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING) = Intent(Intent.ACTION_BATTERY_CHANGED)
        .putExtra(BatteryManager.EXTRA_LEVEL, level).putExtra(BatteryManager.EXTRA_SCALE, scale).putExtra(BatteryManager.EXTRA_STATUS, status)
    @Test fun batteryValuesComeFromTheLocalBroadcastAndNeverDefaultToAnInventedPercentage() {
        assertEquals(PanelBatteryState(), PanelBatteryState.from(null))
        assertEquals(PanelBatteryState(), PanelBatteryState.from(Intent("other")))
        for (invalid in listOf(battery(-1, 100), battery(101, 100), battery(1, 0), battery(1, -1))) {
            assertEquals(PanelBatteryState(), PanelBatteryState.from(invalid))
        }
        assertEquals(PanelBatteryState(75, false), PanelBatteryState.from(battery(3, 4)))
        assertEquals(PanelBatteryState(0, false), PanelBatteryState.from(battery(0, 100)))
        assertEquals(PanelBatteryState(100, true), PanelBatteryState.from(battery(100, 100, BatteryManager.BATTERY_STATUS_FULL)))
        assertEquals(PanelBatteryState(33, true), PanelBatteryState.from(battery(1, 3, BatteryManager.BATTERY_STATUS_CHARGING)))
    }
    @Test fun receiverUpdatesOnlyWhileItsDialIsComposedAndIsUnregisteredOnDestroy() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val activity = controller.get()
        val application = shadowOf(activity.application)
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED)
        val originalCount = application.getReceiversForIntent(intent).size
        var observed = PanelBatteryState()
        val composition = PanelUiTestComposition()
        try {
            activity.setContent(parent = composition.recomposer) {
                val state = rememberPanelBatteryState()
                SideEffect { observed = state }
                Text(state.percent?.toString() ?: "--")
            }
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            content.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            content.layout(0, 0, 800, 400)
            shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            assertEquals(originalCount + 1, application.getReceiversForIntent(intent).size)
            activity.application.sendBroadcast(battery(43, 100, BatteryManager.BATTERY_STATUS_CHARGING))
            val expected = PanelBatteryState(43, true)
            composition.await { observed == expected }
            assertEquals(expected, observed)
        } finally { controller.pause().stop().destroy(); composition.close() }
        assertEquals(originalCount, application.getReceiversForIntent(intent).size)
    }
}

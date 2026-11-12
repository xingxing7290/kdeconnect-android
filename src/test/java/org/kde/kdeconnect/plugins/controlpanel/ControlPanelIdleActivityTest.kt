/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.JsonHelper
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin
import kotlinx.serialization.json.JsonObject
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "w1000dp-h600dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlPanelIdleActivityTest {
    @Test fun realActivityConsumesWakeTapAndRestartsTimerAfterBackground() {
        val application = ApplicationProvider.getApplicationContext<KdeConnect>()
        val id = "panel-idle-activity-test-device"
        val sent = mutableListOf<NetworkPacket>()
        lateinit var plugin: ControlPanelPlugin
        val device = mockk<Device> {
            every { deviceId } returns id
            every { name } returns "Local idle test"
            every { isPaired } returns true
            every { isReachable } returns true
            every { getPlugin(ControlPanelPlugin::class.java) } answers { plugin }
            every { getPlugin(SystemVolumePlugin::class.java) } returns null
            every { addPluginsChangedListener(any()) } returns true
            every { removePluginsChangedListener(any()) } returns true
            every { sendPacket(any(), any()) } answers {
                sent.add(arg(0)); arg<Device.SendPacketStatusCallback>(1).onSuccess()
            }
        }
        plugin = ControlPanelPlugin().apply { setContext(application, device); preferences.edit().clear().commit(); onCreate() }
        val config = PanelConfig.parse(File("docs/control-panel/fixtures/config-v1.json").readText())
        assertTrue(plugin.onPacketReceived(NetworkPacket(ControlPanelPlugin.CONFIG).apply {
            this["protocolVersion"] = 1
            this["config"] = JsonHelper.json.parseToJsonElement(config.encode()) as JsonObject
        }))
        application.devices[id] = device
        val intent = Intent(application, ControlPanelActivity::class.java).putExtra(ControlPanelActivity.DEVICE_ID, id)
        val controller = Robolectric.buildActivity(ControlPanelActivity::class.java, intent).setup().visible()
        try {
            val activity = controller.get()
            val looper = shadowOf(Looper.getMainLooper())
            looper.idleFor(200, TimeUnit.MILLISECONDS)
            val idleField = ControlPanelActivity::class.java.getDeclaredField("idle").apply { isAccessible = true }
            val idle = idleField.get(activity) as PanelIdleController
            fun actions() = sent.filter { it.type == ControlPanelPlugin.REQUEST && it.getString("operation") == "action" }
            fun acknowledge() {
                val action = actions().last()
                plugin.onPacketReceived(NetworkPacket(ControlPanelPlugin.RESULT).apply {
                    this["protocolVersion"] = 1; this["requestId"] = action.getString("requestId")
                    this["success"] = true; this["error"] = ""
                })
            }
            fun tapFirstSlot() {
                val decor = activity.window.decorView
                val x = decor.width * 0.1f
                val y = decor.height * 0.26f
                val time = SystemClock.elapsedRealtime()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(time, time, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                    activity.dispatchTouchEvent(event)
                    event.recycle()
                }
                looper.idleFor(200, TimeUnit.MILLISECONDS)
            }
            tapFirstSlot()
            assertEquals("The test coordinate must first hit a real configured button", 1, actions().size)
            acknowledge()
            val beforeIdle = SystemClock.elapsedRealtime()
            looper.idleFor(5100, TimeUnit.MILLISECONDS)
            assertTrue("The Robolectric monotonic clock must advance with the looper: ${SystemClock.elapsedRealtime() - beforeIdle}", SystemClock.elapsedRealtime() - beforeIdle >= 5100)
            val lastInput = PanelIdleController::class.java.getDeclaredField("lastInput").apply { isAccessible = true }.getLong(idle)
            assertTrue("idle should show at ${SystemClock.elapsedRealtime()} after input at $lastInput", idle.showing.value)
            tapFirstSlot()
            assertFalse(idle.showing.value)
            assertEquals("The wake tap must not reach the button", 1, actions().size)
            tapFirstSlot()
            assertEquals("Only the next tap can activate the button", 2, actions().size)
            acknowledge()
            controller.pause()
            looper.idleFor(10_000, TimeUnit.MILLISECONDS)
            assertFalse(idle.showing.value)
            controller.resume().visible()
            looper.idleFor(4000, TimeUnit.MILLISECONDS)
            assertFalse(idle.showing.value)
            looper.idleFor(1200, TimeUnit.MILLISECONDS)
            assertTrue(idle.showing.value)
        } finally {
            controller.pause().stop().destroy()
            plugin.onDestroy()
            application.devices.remove(id)
            plugin.preferences.edit().clear().commit()
        }
    }
}

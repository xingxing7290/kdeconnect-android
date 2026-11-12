/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.inputdevicesreceiver

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.mousereceiver.MouseReceiverPlugin
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 24, 37])
class InputDevicesReceiverCompatibilityTest {
    @Test fun legacyAndroidReleasesInputInsteadOfCallingUnsupportedMouseActions() {
        val mouse = mockk<MouseReceiverPlugin>()
        val device = mockk<Device> {
            every { deviceId } returns "input-compat-test-device"
            every { getPlugin(MouseReceiverPlugin::class.java) } returns mouse
            every { sendPacket(any()) } returns Unit
        }
        every { mouse.onPacketReceived(any()) } returns true
        val plugin = InputDevicesReceiverPlugin().apply { setContext(ApplicationProvider.getApplicationContext<Context>(), device) }
        val request = NetworkPacket("kdeconnect.shareinputdevices.request").apply {
            set("exitEdge", 0x00004)
            set("deltax", 8)
            set("deltay", 12)
        }
        val accepted = plugin.onPacketReceived(request)
        if (Build.VERSION.SDK_INT < 24) {
            assertFalse(accepted)
            verify(exactly = 0) { mouse.onPacketReceived(any()) }
            verify(exactly = 1) { device.sendPacket(match {
                it.type == "kdeconnect.shareinputdevices" && it.getInt("releaseDeltax") == 8 && it.getInt("releaseDeltay") == 12
            }) }
        } else {
            assertTrue(accepted)
            verify(exactly = 1) { mouse.onPacketReceived(match { it.getInt("x") == 1 && it.getInt("y") == 12 }) }
            verify(exactly = 0) { device.sendPacket(any()) }
        }
        InputDevicesReceiverPlugin.Cursor.enterEdge = 0
    }
}

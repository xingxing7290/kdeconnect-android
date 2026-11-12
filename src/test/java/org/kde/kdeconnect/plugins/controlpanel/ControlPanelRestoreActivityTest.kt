/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.JsonHelper
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28, 37], qualifiers = "zh-w1000dp-h600dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlPanelRestoreActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val application = ApplicationProvider.getApplicationContext<KdeConnect>()
    private val id = "panel-restore-activity-device"
    private val sent = mutableListOf<NetworkPacket>()
    private val listeners = mutableSetOf<Device.PluginsChangedListener>()
    private var paired = true
    private var reachable = true
    private var currentPlugin: ControlPanelPlugin? = null
    private lateinit var device: Device
    private lateinit var cacheOwner: ControlPanelPlugin
    private var controller: ActivityController<ControlPanelActivity>? = null
    private fun fixture() = PanelConfig.parse(File("docs/control-panel/fixtures/config-v1.json").readText()).let {
        it.copy(idle = it.idle.copy(enabled = false))
    }
    private fun configure(plugin: ControlPanelPlugin, config: PanelConfig = fixture()) {
        assertTrue(plugin.onPacketReceived(NetworkPacket(ControlPanelPlugin.CONFIG).apply {
            this["protocolVersion"] = 1
            this["config"] = JsonHelper.json.parseToJsonElement(config.encode()) as JsonObject
        }))
    }
    private fun plugin() = ControlPanelPlugin().apply { setContext(application, device); onCreate() }
    @Before fun setup() {
        device = mockk {
            every { deviceId } returns id
            every { name } returns "Restore test desktop"
            every { isPaired } answers { paired }
            every { isReachable } answers { reachable }
            every { getPlugin(ControlPanelPlugin::class.java) } answers { currentPlugin }
            every { getPlugin(SystemVolumePlugin::class.java) } returns null
            every { addPluginsChangedListener(any()) } answers { listeners.add(firstArg()) }
            every { removePluginsChangedListener(any()) } answers { listeners.remove(firstArg()) }
            every { sendPacket(any(), any()) } answers {
                sent.add(arg(0)); arg<Device.SendPacketStatusCallback>(1).onSuccess()
            }
        }
        application.devices[id] = device
        cacheOwner = ControlPanelPlugin().apply {
            setContext(application, device)
            preferences.edit().clear().commit()
            onCreate()
        }
        configure(cacheOwner)
        cacheOwner.onDestroy()
        reachable = false
        sent.clear()
    }
    private fun open() {
        val intent = Intent(application, ControlPanelActivity::class.java).putExtra(ControlPanelActivity.DEVICE_ID, id)
        controller = Robolectric.buildActivity(ControlPanelActivity::class.java, intent).setup().visible()
    }
    private fun displaySession(): PanelSession? = ControlPanelActivity::class.java.getDeclaredField("displaySession\$delegate").let { field ->
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(requireNotNull(controller).get()) as androidx.compose.runtime.State<PanelSession?>).value
    }
    private fun notifyPluginsChanged() = listeners.toList().forEach { it.onPluginsChanged(device) }
    @After fun cleanup() {
        controller?.pause()?.stop()?.destroy()
        currentPlugin?.onDestroy()
        cacheOwner.preferences.edit().clear().commit()
        application.devices.remove(id)
    }

    @Test fun coldOfflineActivityRestoresDisplayWithoutStartingAPlugin() {
        open()
        compose.waitForIdle()
        assertEquals(fixture(), displaySession()?.state?.value?.config)
        assertEquals(PanelConnection.DISCONNECTED, displaySession()?.state?.value?.connection)
        compose.onNodeWithText("KDE 文档").assertIsDisplayed()
        compose.onNodeWithTag("panel-slot-0").assertIsNotEnabled().performTouchInput { click() }
        assertTrue("Restoring a display must not start network requests", sent.isEmpty())
        assertTrue(displaySession()?.state?.value?.pendingButtons?.isEmpty() == true)
    }

    @Test fun recreatedOfflineActivityWaitsForFreshConfigAfterReconnect() {
        open()
        requireNotNull(controller).recreate().visible()
        compose.waitForIdle()
        assertEquals(fixture(), displaySession()?.state?.value?.config)
        compose.onNodeWithTag("panel-slot-0").assertIsNotEnabled()
        compose.runOnIdle {
            reachable = true
            currentPlugin = plugin()
            notifyPluginsChanged()
        }
        assertSame(requireNotNull(currentPlugin).session, displaySession())
        assertEquals(PanelConnection.SYNCING, displaySession()?.state?.value?.connection)
        compose.onNodeWithTag("panel-slot-0").assertIsNotEnabled().performTouchInput { click() }
        assertTrue(sent.none { it.getString("operation") == "action" })
        compose.runOnIdle { configure(requireNotNull(currentPlugin), fixture().copy(revision = 8)) }
        compose.onNodeWithTag("panel-slot-0").assertIsEnabled().performTouchInput { click() }
        val actions = sent.filter { it.getString("operation") == "action" }
        assertEquals(1, actions.size)
        assertEquals(8L, actions.single().getLong("revision"))
    }

    @Test fun unpairedColdLaunchCannotRestorePreviousPrivateDisplay() {
        paired = false
        open()
        assertTrue(requireNotNull(controller).get().isFinishing)
        assertNull(displaySession())
        assertTrue(sent.isEmpty())
    }

    @Test fun invalidPreferenceTypeCannotCrashOfflineLaunchOrEnableControls() {
        cacheOwner.preferences.edit().putInt("panel_config", 42).commit()
        open()
        compose.waitForIdle()
        assertFalse(requireNotNull(controller).get().isFinishing)
        assertNull(displaySession()?.state?.value?.config)
        assertEquals(PanelConnection.DISCONNECTED, displaySession()?.state?.value?.connection)
        compose.onNodeWithText("KDE 文档").assertDoesNotExist()
        compose.onNodeWithTag("panel-slot-0").assertIsNotEnabled()
        assertTrue(sent.isEmpty())
    }

    @Test fun unpairingAnOpenActivityClearsItsSessionAndClosesIt() {
        open()
        compose.waitForIdle()
        compose.onNodeWithText("KDE 文档").assertIsDisplayed()
        compose.runOnIdle { paired = false; notifyPluginsChanged() }
        assertTrue(requireNotNull(controller).get().isFinishing)
        assertNull(displaySession())
        assertTrue(sent.isEmpty())
    }
}

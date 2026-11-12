/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.JsonHelper
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28, 37], qualifiers = "zh-w1000dp-h600dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlPanelTouchUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null
    private var plugin: ControlPanelPlugin? = null
    private val sent = mutableListOf<NetworkPacket>()
    private var observedFontScale = 0f
    private fun fixture() = PanelConfig.parse(File("docs/control-panel/fixtures/config-v1.json").readText())
    private fun configure(config: PanelConfig) {
        assertTrue(requireNotNull(plugin).onPacketReceived(NetworkPacket(ControlPanelPlugin.CONFIG).apply {
            this["protocolVersion"] = 1
            this["config"] = JsonHelper.json.parseToJsonElement(config.encode()) as JsonObject
        }))
    }
    private fun open(config: PanelConfig = fixture(), fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        val application = ApplicationProvider.getApplicationContext<KdeConnect>()
        val device = mockk<Device> {
            every { deviceId } returns "panel-touch-ui-test-device"
            every { isPaired } returns true
            every { isReachable } returns true
            every { sendPacket(any(), any()) } answers {
                sent.add(arg(0)); arg<Device.SendPacketStatusCallback>(1).onSuccess()
            }
        }
        val control = ControlPanelPlugin().apply {
            setContext(application, device)
            preferences.edit().clear().commit()
            onCreate()
        }
        plugin = control
        configure(config)
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        requireNotNull(controller).get().setContent {
            MaterialTheme {
                val scale = LocalDensity.current.fontScale
                SideEffect { observedFontScale = scale }
                ControlPanelScreen("本地触摸测试", control.session, control, null, onBack = {})
            }
        }
        compose.waitForIdle()
        assertEquals(fontScale, observedFontScale, 0.001f)
    }
    private fun requests(operation: String) = sent.filter { it.type == ControlPanelPlugin.REQUEST && it.getString("operation") == operation }
    @After fun cleanup() {
        controller?.pause()?.stop()?.destroy()
        plugin?.onDestroy()
        plugin?.preferences?.edit()?.clear()?.commit()
    }

    @Test fun touchActivationIsSinglePendingAndOfflineClicksDoNotSend() {
        open()
        val slot = compose.onNodeWithTag("panel-slot-0")
        slot.assertContentDescriptionEquals("KDE 文档").assertIsEnabled().performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, requests("action").size)
        slot.assertIsNotEnabled().performTouchInput { click() }
        assertEquals(1, requests("action").size)
        compose.runOnIdle {
            requireNotNull(plugin).onPacketReceived(NetworkPacket(ControlPanelPlugin.RESULT).apply {
                this["protocolVersion"] = 1
                this["requestId"] = requests("action").single().getString("requestId")
                this["success"] = true
                this["error"] = ""
            })
        }
        slot.assertIsEnabled()
        compose.runOnIdle { requireNotNull(plugin).session.disconnected() }
        slot.assertIsNotEnabled().performTouchInput { click() }
        assertEquals(1, requests("action").size)
    }

    @Test fun largeTextAndHiddenLabelsKeepAccessibleNamesAndTouchTargets() {
        val base = fixture()
        val name = "很长的中文程序名称".repeat(10)
        val page = base.pages.first()
        val slots = page.slots.toMutableList().apply { this[0] = requireNotNull(this[0]).copy(name = name) }
        open(base.copy(appearance = base.appearance.copy(showLabels = false), pages = listOf(page.copy(slots = slots)) + base.pages.drop(1)), 2f)
        for (index in 0 until 18) {
            compose.onNodeWithTag("panel-slot-$index").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithTag("panel-slot-0").assertContentDescriptionEquals(name).performTouchInput { click() }
        compose.onNodeWithText(name).assertDoesNotExist()
        compose.onNodeWithTag("panel-slot-1").assertIsNotEnabled()
        assertEquals(1, requests("action").size)
    }

    @Test fun bothConfiguredSwipeDirectionsRequestTheNextPage() {
        val base = fixture()
        open(base)
        for ((index, direction) in listOf("horizontal", "vertical").withIndex()) {
            compose.runOnIdle { configure(base.copy(revision = 8L + index, appearance = base.appearance.copy(swipeDirection = direction))) }
            val before = requests("page").size
            compose.onNodeWithTag("panel-slot-7").performTouchInput { if (direction == "horizontal") swipeLeft() else swipeUp() }
            compose.waitForIdle()
            assertEquals(before + 1, requests("page").size)
            assertEquals("editor", requests("page").last().getString("pageId"))
            assertTrue(requests("action").isEmpty())
        }
    }

    @Test
    @Config(qualifiers = "zh-w800dp-h360dp-land")
    fun phoneLandscapeAtDoubleTextScaleKeepsAllEighteenTargetsReachable() {
        open(fontScale = 2f)
        for (index in 0 until 18) {
            compose.onNodeWithTag("panel-slot-$index").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithText("KDE 文档").assertIsDisplayed()
        compose.onNodeWithTag("panel-slot-0").assertContentDescriptionEquals("KDE 文档").performTouchInput { click() }
        assertEquals(1, requests("action").size)
    }
}

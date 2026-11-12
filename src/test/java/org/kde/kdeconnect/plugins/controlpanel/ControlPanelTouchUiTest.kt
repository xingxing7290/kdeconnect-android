/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.TextLayoutResult
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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.graphics.ColorUtils
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
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
    @get:Rule val temporary = TemporaryFolder()
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
    private fun open(config: PanelConfig = fixture(), fontScale: Float = 1f, assets: PanelAssetCache? = null, darkTheme: Boolean = false) {
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
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                val scale = LocalDensity.current.fontScale
                SideEffect { observedFontScale = scale }
                ControlPanelScreen("本地触摸测试", control.session, control, null, onBack = {}, assetCache = assets)
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

    @Test
    @Config(sdk = [28, 37], qualifiers = "zh-w800dp-h360dp-land")
    fun loadedIconsAtDoubleTextScaleDoNotClipTheLabelLine() {
        val base = fixture()
        val image = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val bytes = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it); image.recycle() }.toByteArray()
        val iconId = PanelAssetCache.hash(bytes)
        val assets = PanelAssetCache(temporary.newFolder())
        assertTrue(assets.accept(iconId, bytes))
        val firstPage = base.pages.first()
        val button = requireNotNull(firstPage.slots[0]).copy(icon = iconId)
        val slots = firstPage.slots.toMutableList().apply { this[0] = button }
        open(base.copy(appearance = base.appearance.copy(showVolume = false), pages = listOf(firstPage.copy(slots = slots)) + base.pages.drop(1)), 2f, assets)
        compose.runOnIdle { requireNotNull(plugin).session.disconnected() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("panel-button-image-${button.id}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("panel-button-image-${button.id}", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val label = compose.onNodeWithTag("panel-slot-label-0", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val textLayout = layouts.single()
        val visible = label.fetchSemanticsNode().boundsInRoot
        val slot = compose.onNodeWithTag("panel-slot-0").fetchSemanticsNode().boundsInRoot
        assertTrue("Label must retain its full line height", visible.height >= textLayout.getLineBottom(0) - 1f)
        assertTrue("Label must remain inside its button", visible.bottom <= slot.bottom && visible.top >= slot.top)
        compose.onNodeWithTag("panel-slot-0").assertContentDescriptionEquals(button.name).assertIsNotEnabled()
        assertTrue(requests("action").isEmpty())
    }

    private fun checkCustomBackgrounds(fontScale: Float, darkTheme: Boolean) {
        val base = fixture().copy(mode = "follow")
        open(base, fontScale, darkTheme = darkTheme)
        val failures = mutableListOf<String>()
        val colors = listOf("#F4F6FA", "#FFFFFF", "#000000", "#25334A", "#757575", "#767676", "#FF0000", "#00FF00", "#0000FF")
        val control = requireNotNull(plugin)
        for ((index, color) in colors.withIndex()) {
            val config = base.copy(revision = 8L + index, appearance = base.appearance.copy(background = color))
            compose.runOnIdle {
                control.session.connected()
                configure(config)
                assertTrue(control.session.receiveState(config.revision, config.activePageId, "synthetic-editor.exe"))
                control.iconProblem.value = true
            }
            for (status in listOf("success", "failure", "offline", "syncing", "error")) {
                compose.runOnIdle {
                    when (status) {
                        "success", "failure" -> {
                            val request = control.session.action("docs")
                            assertTrue(control.session.receiveResult(PanelResult(request.requestId, status == "success", if (status == "failure") "launch_failed" else "")))
                        }
                        "offline" -> control.session.disconnected()
                        "syncing" -> control.session.connected()
                        "error" -> control.session.failed("sync_timeout")
                    }
                }
                compose.waitForIdle()
                compose.onNodeWithTag("panel-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10_000f) }
                val content = requireNotNull(controller).get().findViewById<ViewGroup>(android.R.id.content)
                val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
                compose.runOnIdle { content.draw(Canvas(bitmap)) }
                val background = bitmap.getPixel(0, 0)
                assertEquals("The configured flat background must really be drawn", android.graphics.Color.parseColor(color), background)
                val scene = "panel-${color.drop(1)}-$status-font$fontScale-sdk${android.os.Build.VERSION.SDK_INT}"
                try {
                    System.getenv("KDECONNECT_PANEL_SCREENSHOTS")?.let { directory ->
                        val file = File(directory, "$scene.png")
                        check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
                        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    }
                } finally {
                    bitmap.recycle()
                }
                val rows = mutableListOf("text\tforegroundARGB\tbackgroundARGB\tcontrast")
                val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
                var checked = 0
                for (node in nodes) {
                    // Card labels have their own light surface; disabled empty slots are decorative.
                    if (node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("panel-slot-label-") == true) continue
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                    val layout = layouts.single()
                    if (layout.layoutInput.text.text == "+") continue
                    val foreground = layout.layoutInput.style.color
                    assertNotEquals(Color.Unspecified, foreground)
                    val ratio = ColorUtils.calculateContrast(foreground.toArgb(), background)
                    val label = layout.layoutInput.text.text
                    rows += "${label.replace('\t', ' ').replace('\n', ' ')}\t${foreground.toArgb().toUInt().toString(16)}\t${background.toUInt().toString(16)}\t$ratio"
                    if (ratio < 4.5) failures += "$scene / $label: $ratio below 4.5"
                    val textNode = compose.onNode(SemanticsMatcher("Text ${node.id}") { it.id == node.id }, useUnmergedTree = true)
                    // Page names also have a horizontal scroll parent; expose their footer first.
                    if (label.startsWith("1  ") || label.startsWith("2  "))
                        compose.onNodeWithTag("panel-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
                    textNode.performScrollTo().assertIsDisplayed()
                    val bounds = textNode.fetchSemanticsNode().boundsInRoot
                    if (bounds.height < layout.getLineBottom(layout.lineCount - 1) - 1f || bounds.top < 0 || bounds.bottom > content.height)
                        failures += "$scene / $label: clipped text"
                    checked++
                }
                assertTrue("Each scene must check header, pages, volume and status text", checked >= 5)
                System.getenv("KDECONNECT_PANEL_CONTRAST_REPORTS")?.let { directory ->
                    val file = File(directory, "$scene.tsv")
                    check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
                    file.writeText(rows.joinToString("\n"))
                }
                if (status == "offline") compose.onNodeWithTag("panel-slot-0").assertIsNotEnabled().performTouchInput { click() }
            }
        }
        assertTrue("Contrast checks must not trigger remote actions", requests("action").isEmpty())
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    @Config(qualifiers = "zh-w800dp-h360dp-land")
    fun customBackgroundsKeepRootTextReadable() = checkCustomBackgrounds(1f, false)

    @Test
    @Config(qualifiers = "en-w800dp-h360dp-land")
    fun customBackgroundsKeepDoubleScaleDarkThemeTextReadable() = checkCustomBackgrounds(2f, true)

    @Test
    @Config(qualifiers = "en-w640dp-h360dp-land")
    fun crowdedDoubleScaleMessagesKeepAllTargetsAndBothPageGesturesUsable() {
        val base = fixture()
        open(base, 2f, darkTheme = true)
        val control = requireNotNull(plugin)
        for ((index, direction) in listOf("horizontal", "vertical").withIndex()) {
            compose.runOnIdle {
                configure(base.copy(revision = 8L + index, appearance = base.appearance.copy(background = "#000000", swipeDirection = direction)))
                control.iconProblem.value = true
                assertTrue(control.activate("docs"))
                assertTrue(control.onPacketReceived(NetworkPacket(ControlPanelPlugin.RESULT).apply {
                    this["protocolVersion"] = 1
                    this["requestId"] = requests("action").last().getString("requestId")
                    this["success"] = false
                    this["error"] = "launch_failed"
                }))
            }
            for (slot in 0 until 18)
                compose.onNodeWithTag("panel-slot-$slot").performScrollTo().assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("panel-action-result").performScrollTo().assertIsDisplayed()
            System.getenv("KDECONNECT_PANEL_SCREENSHOTS")?.let { directory ->
                val content = requireNotNull(controller).get().findViewById<ViewGroup>(android.R.id.content)
                val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
                try {
                    compose.runOnIdle { content.draw(Canvas(bitmap)) }
                    val file = File(directory, "panel-crowded-$direction-footer-sdk${android.os.Build.VERSION.SDK_INT}.png")
                    check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
            }
            val before = requests("page").size
            compose.onNodeWithTag("panel-grid").performScrollTo().performTouchInput {
                if (direction == "horizontal") swipeLeft() else swipeUp()
            }
            compose.waitForIdle()
            assertEquals(before + 1, requests("page").size)
            assertEquals("editor", requests("page").last().getString("pageId"))
            assertEquals("Scrolling and swiping must not execute any extra action", index + 1, requests("action").size)
        }
    }
}

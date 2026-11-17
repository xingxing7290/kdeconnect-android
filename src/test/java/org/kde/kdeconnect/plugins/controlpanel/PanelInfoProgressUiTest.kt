/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Calendar
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28, 37], qualifiers = "zh-rCN-w800dp-h360dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelInfoProgressUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun sessionPacketsUpdateInformationDialAcrossConfigurationAndDisconnect() {
        val calendar = Calendar.getInstance()
        val seconds = calendar.timeInMillis / 1000
        val idle = PanelIdle(true, 5000, "info", "", "", PanelInfo(showForecast = false, showProgress = true))
        val config = PanelConfig(1, 1, "single", "home", "home", listOf(PanelPage("home", "Home", slots = List(18) { null })),
            PanelAppearance("#112233", true, true, "horizontal"), idle)
        val session = PanelSession()
        session.connected()
        assertTrue(session.receiveConfig(config.encode()))
        fun packet(status: String, title: String, sequence: Int, percent: String = "") =
            """{"source":"workbuddy","status":"$status","title":"$title","stage":"阶段","detail":"","sequence":$sequence,"updatedAt":$seconds,"fetchedAt":$seconds$percent}"""
        assertTrue(session.receiveProgress(1, packet("running", "旧任务", 1, ",\"percent\":42"), seconds))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            controller.get().setContent {
                val state by session.state.collectAsState()
                MaterialTheme {
                    PanelInformationDial(checkNotNull(state.config).idle, calendar, null, Modifier.fillMaxSize(),
                        progress = state.progress, connection = state.connection, nowSeconds = seconds)
                }
            }
            compose.waitForIdle()
            compose.onNodeWithTag("panel-progress-title", useUnmergedTree = true).assertTextEquals("旧任务")
            compose.onNodeWithTag("panel-progress-percent", useUnmergedTree = true).assertTextEquals("42%")
            compose.runOnIdle {
                assertTrue(session.receiveConfig(config.copy(revision = 2).encode()))
                assertTrue(session.receiveProgress(2, packet("unavailable", "", 2), seconds))
            }
            compose.onNodeWithTag("panel-progress-state", useUnmergedTree = true).assertTextEquals("等待 WorkBuddy 进度")
            compose.onNodeWithTag("panel-progress-percent", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnIdle { assertTrue(session.receiveProgress(2, packet("running", "新任务", 3), seconds)) }
            compose.onNodeWithTag("panel-progress-title", useUnmergedTree = true).assertTextEquals("新任务")
            compose.runOnIdle { session.disconnected() }
            compose.onNodeWithTag("panel-progress-state", useUnmergedTree = true).assertTextEquals("离线 · 最后一次进度")
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w640dp-h180dp-land")
    fun compactOfflineStatusRemainsVisibleWithLongStageAndLargeFont() {
        RuntimeEnvironment.setFontScale(2f)
        val calendar = Calendar.getInstance()
        val seconds = calendar.timeInMillis / 1000
        val progress = PanelWorkProgress("workbuddy", "running", "任务", "很长的阶段".repeat(20), "", 42, 1, seconds, seconds)
        val options = PanelIdle(true, 5000, "info", "", "", PanelInfo(showForecast = false, showProgress = true))
        var observedFontScale = 0f
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            controller.get().setContent {
                MaterialTheme {
                    val fontScale = LocalDensity.current.fontScale
                    SideEffect { observedFontScale = fontScale }
                    PanelInformationDial(options, calendar, null, Modifier.fillMaxSize(),
                        progress = progress, connection = PanelConnection.DISCONNECTED, nowSeconds = seconds)
                }
            }
            compose.waitForIdle()
            assertEquals(2f, observedFontScale, 0.001f)
            val node = compose.onNodeWithTag("panel-progress-compact", useUnmergedTree = true)
            node.assertIsDisplayed()
            val semantics = node.fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            semantics.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            val text = layout.layoutInput.text.text
            val status = "离线 · 最后一次进度"
            val statusEnd = text.indexOf(status) + status.length
            assertTrue("Offline status must be present", statusEnd >= status.length)
            assertTrue("Offline status must remain visible before ellipsis", layout.getLineEnd(0, visibleEnd = true) >= statusEnd)
            assertTrue("Progress line must fit its vertical bounds", semantics.boundsInRoot.height >= layout.getLineBottom(0) - 1f)
        } finally {
            controller.pause().stop().destroy()
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w800dp-h180dp-land")
    fun compactModeHonorsForecastAndDistinguishesProgressStates() {
        val calendar = Calendar.getInstance()
        val seconds = calendar.timeInMillis / 1000
        val time = mutableStateOf(seconds)
        val connection = mutableStateOf(PanelConnection.READY)
        val progress = mutableStateOf<PanelWorkProgress?>(PanelWorkProgress(
            "workbuddy", "running", "任务", "处理中", "", 42, 1, seconds, seconds,
        ))
        val options = PanelIdle(true, 5000, "info", "", "", PanelInfo(showForecast = false, showProgress = true))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            controller.get().setContent {
                MaterialTheme {
                    PanelInformationDial(options, calendar, null, Modifier.fillMaxSize(),
                        progress = progress.value, connection = connection.value, nowSeconds = time.value)
                }
            }
            compose.waitForIdle()
            compose.onNodeWithTag("panel-progress-compact", useUnmergedTree = true).assertTextContains("42%", substring = true)
            compose.onNodeWithTag("panel-forecast-0", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnIdle { time.value = seconds + 20 }
            compose.onNodeWithTag("panel-progress-compact", useUnmergedTree = true).assertTextContains("进度已过期", substring = true)
            compose.runOnIdle { connection.value = PanelConnection.DISCONNECTED }
            compose.onNodeWithTag("panel-progress-compact", useUnmergedTree = true).assertTextContains("离线", substring = true)
            compose.runOnIdle { connection.value = PanelConnection.READY; progress.value = null }
            compose.onNodeWithTag("panel-progress-compact", useUnmergedTree = true).assertTextContains("等待 WorkBuddy 进度", substring = true)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun progressNotesAndForecastFollowTheConfiguration() {
        val calendar = Calendar.getInstance()
        val seconds = calendar.timeInMillis / 1000
        val time = mutableStateOf(seconds)
        val connection = mutableStateOf(PanelConnection.READY)
        val progress = mutableStateOf(PanelWorkProgress(
            "workbuddy", "running", "测试任务", "正在处理", "测试详情", 42, 1, seconds - 5, seconds,
        ))
        val options = PanelIdle(true, 5000, "info", "", "", PanelInfo(
            notes = listOf("第一条备注", "第二条备注"), showForecast = false, showProgress = true,
        ))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            controller.get().setContent {
                MaterialTheme {
                    PanelInformationDial(options, calendar, null, Modifier.fillMaxSize(),
                        progress = progress.value, connection = connection.value, nowSeconds = time.value)
                }
            }
            compose.waitForIdle()
            compose.onNodeWithText("· 第一条备注", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("· 第二条备注", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("panel-progress-state", useUnmergedTree = true).assertTextEquals("进行中")
            compose.onNodeWithTag("panel-progress-percent", useUnmergedTree = true).assertTextEquals("42%")
            compose.onNodeWithTag("panel-forecast-0", useUnmergedTree = true).assertDoesNotExist()

            compose.runOnIdle { progress.value = progress.value.copy(percent = null) }
            compose.onNodeWithTag("panel-progress-percent", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnIdle { time.value = seconds + 20 }
            compose.onNodeWithTag("panel-progress-state", useUnmergedTree = true).assertTextEquals("进度已过期，等待更新")
            compose.runOnIdle { connection.value = PanelConnection.DISCONNECTED }
            compose.onNodeWithTag("panel-progress-state", useUnmergedTree = true).assertTextEquals("离线 · 最后一次进度")
            compose.runOnIdle {
                connection.value = PanelConnection.READY
                progress.value = progress.value.copy(status = "unavailable")
            }
            compose.onNodeWithTag("panel-progress-state", useUnmergedTree = true).assertTextEquals("等待 WorkBuddy 进度")
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

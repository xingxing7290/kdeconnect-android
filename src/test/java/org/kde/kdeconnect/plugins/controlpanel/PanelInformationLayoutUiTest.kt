/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Calendar
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28, 37], qualifiers = "zh-rCN-w851dp-h393dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelInformationLayoutUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun currentConditionsAndDailyRangeKeepTheirCompleteTextLines() {
        val now = Calendar.getInstance()
        val seconds = now.timeInMillis / 1000
        val forecast = (0..2).map { day ->
            PanelForecastDay(PanelWeatherSnapshot.dateAtOffset(seconds + day * 86400, 28800), 13.0, 16.0, 3)
        }
        val weather = PanelWeatherSnapshot("长沙市", "ready", "open-meteo", "长沙市 / 中国", seconds - 300, seconds, 14.2, 3,
            timezone = "Asia/Shanghai", utcOffsetSeconds = 28800, forecast = forecast)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            controller.get().setContent {
                MaterialTheme {
                    PanelDialScreen(PanelIdle(true, 5000, "info", "长沙市", "Synthetic layout fixture"), PanelConnection.READY, {}, weather)
                }
            }
            compose.waitForIdle()
            val region = compose.onNodeWithTag("panel-info-current-weather", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            for (tag in listOf("panel-weather-temperature", "panel-weather-status", "panel-info-today-range")) {
                val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val text = layouts.single()
                val visible = node.fetchSemanticsNode().boundsInRoot
                assertTrue("$tag must retain every displayed line", visible.height >= text.getLineBottom(text.lineCount - 1) - 1f)
                assertTrue("$tag must remain inside the current-weather border", visible.top >= region.top && visible.bottom <= region.bottom)
                if (tag == "panel-info-today-range") assertEquals("13° / 16°", text.layoutInput.text.text)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

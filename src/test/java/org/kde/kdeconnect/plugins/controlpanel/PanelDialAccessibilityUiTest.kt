/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.os.ConfigurationCompat
import androidx.core.graphics.ColorUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect_tp.R
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28, 37], qualifiers = "zh-rCN-w800dp-h360dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelDialAccessibilityUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var controller: ActivityController<ComponentActivity>? = null
    private val selectedDial = mutableStateOf("digital")
    private val currentWeather = mutableStateOf<PanelWeatherSnapshot?>(null)
    private val connection = mutableStateOf(PanelConnection.READY)
    private val city = "很长的中文天气测试城市".repeat(20).take(120)
    private val resolvedCity = "很长的中文地理解析城市和地区名称".repeat(20).take(260)
    private val message = "这是一段用于检查表盘布局和完整无障碍文本的说明。".repeat(10).take(240)
    private var wakes = 0
    private var observedFontScale = 0f

    private fun open(dial: String, scale: Float) {
        RuntimeEnvironment.setFontScale(scale)
        selectedDial.value = dial
        val seconds = System.currentTimeMillis() / 1000
        val weather = PanelWeatherSnapshot(city, "ready", "open-meteo", resolvedCity, seconds - 300, seconds, 14.2, 3,
            "Asia/Shanghai", 28800, (0..2).map {
                PanelForecastDay(PanelWeatherSnapshot.dateAtOffset(seconds + it * 86400, 28800), 13.0, 16.0, 3)
            })
        assertNotNull("The synthetic maximum-length observation must satisfy the production parser", PanelWeatherSnapshot.parse(weather.encode()))
        currentWeather.value = weather
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        requireNotNull(controller).get().setContent {
            MaterialTheme {
                val density = LocalDensity.current
                SideEffect { observedFontScale = density.fontScale }
                PanelDialScreen(PanelIdle(true, 5000, selectedDial.value, city, message), connection.value, { wakes++ }, currentWeather.value)
            }
        }
        compose.waitForIdle()
        assertEquals(scale, observedFontScale, 0.001f)
    }

    @After fun cleanup() {
        controller?.pause()?.stop()?.destroy()
    }

    private fun assertReadable(dial: String, scale: Float, variant: String = "") {
        val screen = compose.onNodeWithTag("panel-dial-$dial", useUnmergedTree = true)
        val root = screen.fetchSemanticsNode().boundsInRoot
        assertTrue("The test must exercise a phone-sized landscape surface", root.width > root.height && root.width / root.height < 3f)
        val content = requireNotNull(controller).get().findViewById<ViewGroup>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { content.draw(Canvas(bitmap)) }
        val background = bitmap.getPixel(0, 0)
        try {
            assertEquals("The dial background must be opaque", 255, android.graphics.Color.alpha(background))
            for ((x, y) in listOf(0 to bitmap.height - 1, bitmap.width - 1 to 0, bitmap.width - 1 to bitmap.height - 1))
                assertEquals("Current dial contrast checks require the actual flat background", background, bitmap.getPixel(x, y))
            System.getenv("KDECONNECT_PANEL_SCREENSHOTS")?.let { directory ->
                val file = File(directory, "dial-$dial-font$scale-sdk${android.os.Build.VERSION.SDK_INT}$variant.png")
                check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
        } finally {
            bitmap.recycle()
        }
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        val activity = requireNotNull(controller).get()
        val observation = requireNotNull(currentWeather.value)
        val locale = ConfigurationCompat.getLocales(activity.resources.configuration)[0]
        val formatter = SimpleDateFormat("MM-dd HH:mm", locale)
        val updated = activity.getString(R.string.control_panel_weather_updated, formatter.format(Date(observation.observedAt!! * 1000)),
            formatter.format(Date(observation.fetchedAt!! * 1000)))
        val caption = activity.getString(R.string.control_panel_dial_car_art)
        val labels = texts.flatMap { it.config[SemanticsProperties.Text].map { value -> value.text } }
        if (dial in listOf("digital", "car", "info")) assertTrue("The clock must not be dropped", labels.any { it.matches(Regex("\\d{2}:\\d{2}(:\\d{2})?")) })
        if (dial != "info") assertTrue("The calendar date must not be dropped", labels.any { it.matches(Regex("\\d{4}/\\d{2}/\\d{2}.*")) })
        if (dial in listOf("digital", "weather", "info")) {
            val fresh = observation.fresh(System.currentTimeMillis() / 1000)
            compose.onNodeWithTag("panel-weather-temperature", useUnmergedTree = true).assertTextEquals(if (fresh) "${observation.temperature!!.roundToInt()}°C" else "--°C")
            assertTrue("The observation time must not be dropped", updated in labels)
        }
        if (dial == "info") {
            compose.onNodeWithTag("panel-local-battery", useUnmergedTree = true).assertIsDisplayed()
            for (day in 0..2) compose.onNodeWithTag("panel-forecast-$day", useUnmergedTree = true).assertIsDisplayed()
        }
        val problems = mutableListOf<String>()
        val contrasts = mutableListOf("text\tforegroundARGB\tbackgroundARGB\tcontrast")
        for (node in texts) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            val bounds = node.boundsInRoot
            val label = layout.layoutInput.text.text.take(35)
            val foreground = layout.layoutInput.style.color
            assertNotEquals("Each text must expose its effective foreground", Color.Unspecified, foreground)
            val contrast = ColorUtils.calculateContrast(foreground.toArgb(), background)
            contrasts += "${layout.layoutInput.text.text.replace('\t', ' ').replace('\n', ' ')}\t${foreground.toArgb().toUInt().toString(16)}\t${background.toUInt().toString(16)}\t$contrast"
            if (contrast < 4.5) problems += "$label: contrast $contrast is below 4.5"
            if (layout.lineCount < 1 || bounds.height < layout.getLineBottom(layout.lineCount - 1) - 1f)
                problems += "$label: displayed lines are clipped (${bounds.height} px, ${layout.size.height} px layout)"
            if (bounds.left < root.left - 1f || bounds.right > root.right + 1f || bounds.top < root.top - 1f || bounds.bottom > root.bottom + 1f)
                problems += "$label: outside the display"
            val singleValue = label.matches(Regex("\\d{2}:\\d{2}(:\\d{2})?")) || label.matches(Regex("(-?\\d+|--)°C"))
            if ((singleValue || label.contains('°') || layout.layoutInput.text.text in listOf(updated, caption)) &&
                ((singleValue && layout.lineCount != 1) || layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) != layout.layoutInput.text.length ||
                    (0 until layout.lineCount).any { layout.isLineEllipsized(it) || layout.getLineRight(it) - layout.getLineLeft(it) > bounds.width + 1f }))
                problems += "$label: essential characters do not fit"
        }
        System.getenv("KDECONNECT_PANEL_CONTRAST_REPORTS")?.let { directory ->
            val file = File(directory, "contrast-$dial-font$scale-sdk${android.os.Build.VERSION.SDK_INT}$variant.tsv")
            check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
            file.writeText(contrasts.joinToString("\n"))
        }
        for (first in texts.indices) for (second in first + 1 until texts.size) {
            val a = texts[first].boundsInRoot
            val b = texts[second].boundsInRoot
            if (minOf(a.right, b.right) - maxOf(a.left, b.left) > 1f && minOf(a.bottom, b.bottom) - maxOf(a.top, b.top) > 1f)
                problems += "Text regions overlap: ${texts[first].config[SemanticsProperties.Text]} / ${texts[second].config[SemanticsProperties.Text]}"
        }
        if (dial in listOf("weather", "info")) compose.onNodeWithText(resolvedCity, useUnmergedTree = true).assertExists()
        if (dial in listOf("analog", "weather")) {
            val clock = SemanticsMatcher("Spoken analog time") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.matches(Regex("\\d{2}:\\d{2}:\\d{2}")) } == true
            }
            assertEquals("Analog time must remain available to accessibility", 1, compose.onAllNodes(clock, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
        if (dial != "analog") compose.onNodeWithText(message, useUnmergedTree = true).assertExists()
        screen.assertContentDescriptionEquals(requireNotNull(controller).get().getString(R.string.control_panel_dial_wake))
        val before = wakes
        screen.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        assertEquals("Accessible activation must wake exactly once", before + 1, wakes)
        assertTrue("$dial at $scale: ${problems.joinToString("; ")}", problems.isEmpty())
    }

    @Test fun digitalAtDoubleScaleKeepsAllTextReadable() { open("digital", 2f); assertReadable("digital", 2f) }
    @Test fun analogAtDoubleScaleKeepsAllTextReadable() { open("analog", 2f); assertReadable("analog", 2f) }
    @Test fun weatherAtDoubleScaleKeepsAllTextReadable() { open("weather", 2f); assertReadable("weather", 2f) }
    @Test fun informationAtDoubleScaleKeepsAllTextReadable() { open("info", 2f); assertReadable("info", 2f) }
    @Test fun carAtDoubleScaleKeepsAllTextReadable() { open("car", 2f); assertReadable("car", 2f) }
    @Test fun offlineInformationKeepsReadableColorsAndAnAccessibleWakeAction() {
        open("info", 2f)
        compose.runOnIdle { connection.value = PanelConnection.DISCONNECTED }
        assertReadable("info", 2f, "-offline-color")
    }
    @Test fun contrastCalculationIncludesAlphaAndDoesNotRoundUpThresholds() {
        assertEquals(21.0, ColorUtils.calculateContrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.000001)
        assertEquals(1.0, ColorUtils.calculateContrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.000001)
        assertTrue(ColorUtils.calculateContrast(0xFF777777.toInt(), 0xFFFFFFFF.toInt()) < 4.5)
        val translucent = ColorUtils.calculateContrast(0x7F000000, 0xFFFFFFFF.toInt())
        assertTrue(translucent > 3.9 && translucent < 4.1)
    }
    @Test fun allFiveAtDefaultScaleKeepAllTextReadable() {
        open("digital", 1f)
        for (dial in listOf("digital", "analog", "weather", "info", "car")) {
            compose.runOnIdle { selectedDial.value = dial }
            assertReadable(dial, 1f)
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w640dp-h360dp-land")
    fun cachedStaleAndOfflineWeatherRemainReadableAtDoubleScale() {
        open("digital", 2f)
        val fresh = requireNotNull(currentWeather.value)
        for ((index, state) in listOf(
            fresh.copy(status = "unavailable") to PanelConnection.READY,
            fresh.copy(observedAt = fresh.observedAt!! - 12_000, fetchedAt = fresh.fetchedAt!! - 12_000) to PanelConnection.READY,
            fresh to PanelConnection.DISCONNECTED,
        ).withIndex()) {
            compose.runOnIdle { currentWeather.value = state.first; connection.value = state.second }
            for (dial in listOf("digital", "weather", "info")) {
                compose.runOnIdle { selectedDial.value = dial }
                assertReadable(dial, 2f, "-state$index")
            }
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w640dp-h360dp-land")
    fun extremeValidTemperaturesKeepAllDigitsVisibleAtDoubleScale() {
        open("digital", 2f)
        val fresh = requireNotNull(currentWeather.value)
        for ((index, temperature) in listOf(-99.9, 69.9).withIndex()) {
            compose.runOnIdle { currentWeather.value = fresh.copy(temperature = temperature,
                forecast = fresh.forecast.map { it.copy(minimum = -100.0, maximum = 70.0) }) }
            for (dial in listOf("digital", "weather", "info")) {
                compose.runOnIdle { selectedDial.value = dial }
                assertReadable(dial, 2f, "-extreme$index")
            }
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w640dp-h360dp-land")
    fun narrowerLandscapeKeepsAllFiveDialsReadableAtDoubleScale() {
        open("digital", 2f)
        for (dial in listOf("digital", "analog", "weather", "info", "car")) {
            compose.runOnIdle { selectedDial.value = dial }
            assertReadable(dial, 2f, "-narrow")
        }
    }

    @Test
    @Config(qualifiers = "en-w640dp-h360dp-land")
    fun englishLandscapeKeepsAllFiveDialsReadableAtDoubleScale() {
        open("digital", 2f)
        for (dial in listOf("digital", "analog", "weather", "info", "car")) {
            compose.runOnIdle { selectedDial.value = dial }
            assertReadable(dial, 2f, "-english")
        }
    }
}

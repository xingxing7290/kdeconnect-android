/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "w1000dp-h600dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelDialScreenTest {
    @Test fun allFiveDialsRenderWithoutDeviceOrInventedWeather() {
        val observation = System.getenv("KDECONNECT_WEATHER_LIVE_FIXTURE")?.let { file ->
            checkNotNull(PanelWeatherSnapshot.parse(File(file).readText())).also { assertTrue(it.fresh(System.currentTimeMillis() / 1000)) }
        }
        for (dial in listOf("digital", "analog", "weather", "info", "car")) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            try {
                val activity = controller.get()
                activity.setContent {
                    MaterialTheme { PanelDialScreen(PanelIdle(true, 5000, dial, observation?.city ?: "长沙市", "Stay curious"), PanelConnection.READY, {}, observation) }
                }
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                content.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
                content.layout(0, 0, 1200, 600)
                shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
                assertTrue(content.childCount > 0)
                assertTrue(content.width > 0 && content.height > 0)
                val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
                content.draw(Canvas(bitmap))
                val colors = mutableSetOf<Int>()
                for (x in 0 until bitmap.width step 10) for (y in 0 until bitmap.height step 10) colors.add(bitmap.getPixel(x, y))
                assertTrue("$dial must draw content, not a blank surface", colors.size > 3)
                System.getenv("KDECONNECT_PANEL_SCREENSHOTS")?.let { directory ->
                    val file = File(directory, "android-dial-$dial${if (observation != null) "-live" else ""}.png")
                    check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
                bitmap.recycle()
            } finally { controller.pause().stop().destroy() }
        }
    }
}

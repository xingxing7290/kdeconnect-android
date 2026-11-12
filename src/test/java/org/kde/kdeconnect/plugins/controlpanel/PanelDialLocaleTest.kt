/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 28, 37])
class PanelDialLocaleTest {
    @Test fun localeChangesRecomposeTheDialWithoutAnActivityRestart() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val composition = PanelUiTestComposition()
        try {
            val activity = controller.get()
            val configuration = mutableStateOf(Configuration(activity.resources.configuration).apply { setLocale(Locale.US) })
            var observed: Locale? = null
            activity.setContent(parent = composition.recomposer) {
                CompositionLocalProvider(LocalConfiguration provides configuration.value) {
                    val locale = panelDisplayLocale()
                    SideEffect { observed = locale }
                    Text(locale.language)
                }
            }
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            content.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            content.layout(0, 0, 800, 400)
            shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            assertEquals("en", observed?.language)
            val translated = Configuration(configuration.value).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }
            assertEquals("zh", ConfigurationCompat.getLocales(translated)[0]?.language)
            assertFalse("The replacement configuration must differ from the original", configuration.value == translated)
            configuration.value = translated
            assertEquals("The observable configuration must contain the replacement locale", "zh", ConfigurationCompat.getLocales(configuration.value)[0]?.language)
            composition.await { observed?.language == "zh" }
            assertEquals("zh", observed?.language)
        } finally { controller.pause().stop().destroy(); composition.close() }
    }
}

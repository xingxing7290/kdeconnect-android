/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28, 37])
class PanelLocalWeatherPermissionUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun deniedPermissionRefreshesAfterWeatherWasInitiallyDisabled() = verifyPermissionRefresh(false, false)

    @Test fun coarsePermissionRefreshesAfterWeatherWasInitiallyDisabled() = verifyPermissionRefresh(true, false)

    @Test fun finePermissionRefreshesAfterWeatherWasInitiallyDisabled() = verifyPermissionRefresh(false, true)

    private fun verifyPermissionRefresh(coarse: Boolean, fine: Boolean) {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        val preferences = app.getSharedPreferences("panel_local_weather", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val enabled = mutableStateOf(false)
        var displayedCity: String? = null
        var capturedRequestCode = -1
        val registry = object : ActivityResultRegistry() {
            override fun <I : Any?, O : Any?> onLaunch(
                requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?,
            ) { capturedRequestCode = requestCode }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            assertFalse(PanelLocalWeather.hasLocationPermission(controller.get()))
            controller.get().setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    val weather by rememberLocalWeather(enabled.value, "")
                    androidx.compose.runtime.SideEffect { displayedCity = weather?.city }
                    Text(weather?.city ?: "not loaded")
                }
            }
            compose.waitForIdle()
            compose.runOnIdle { enabled.value = true }
            compose.waitForIdle()
            compose.waitUntil(5000) { capturedRequestCode >= 0 }
            compose.waitUntil(5000) { displayedCity == "" }
            val cached = PanelWeatherSnapshot("Cached City", "unavailable", "open-meteo")
            compose.runOnIdle {
                preferences.edit().putString("configured_city", "").putString("snapshot", cached.encode()).commit()
                if (coarse) shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (fine) shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                assertTrue(registry.dispatchResult(capturedRequestCode, mapOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION to coarse,
                    Manifest.permission.ACCESS_FINE_LOCATION to fine,
                )))
            }
            compose.waitForIdle()
            compose.waitUntil(5000) { displayedCity == "Cached City" }
        } finally {
            controller.pause().stop().destroy()
            preferences.edit().clear().commit()
            shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

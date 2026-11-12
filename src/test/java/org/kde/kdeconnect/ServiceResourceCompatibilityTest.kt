/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect_tp.R
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 24, 29, 30, 37])
class ServiceResourceCompatibilityTest {
    @Suppress("DEPRECATION")
    @Test fun versionedServicesAreEnabledOnlyWhenTheirFrameworkBaseClassExists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cases = listOf(
            Triple("org.kde.kdeconnect.plugins.clipboard.ClipboardTileService", Build.VERSION.SDK_INT >= 24, "android.permission.BIND_QUICK_SETTINGS_TILE"),
            Triple("org.kde.kdeconnect.plugins.runcommand.RunCommandControlsProviderService", Build.VERSION.SDK_INT >= 30, "android.permission.BIND_CONTROLS")
        )
        assertEquals(Build.VERSION.SDK_INT >= 24, context.resources.getBoolean(R.bool.clipboard_tile_service_enabled))
        assertEquals(Build.VERSION.SDK_INT >= 30, context.resources.getBoolean(R.bool.runcommand_controls_service_enabled))
        for ((name, enabled, permission) in cases) {
            val service = context.packageManager.getServiceInfo(ComponentName(context.packageName, name), PackageManager.MATCH_DISABLED_COMPONENTS)
            assertEquals(name, enabled, service.enabled)
            assertEquals(permission, service.permission)
            assertTrue(service.exported)
        }
    }

    @Test fun preferenceIconSpaceResolvesToTheExistingFalseValueForNarrowAndWideScreens() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (width in listOf(320, 359, 360, 600)) {
            val configuration = Configuration(context.resources.configuration).apply { smallestScreenWidthDp = width }
            assertFalse("sw${width}dp", context.createConfigurationContext(configuration).resources.getBoolean(R.bool.config_materialPreferenceIconSpaceReserved))
        }
    }
}

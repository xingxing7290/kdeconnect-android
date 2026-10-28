/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin
import org.kde.kdeconnect.ui.compose.KdeTheme

class ControlPanelActivity : AppCompatActivity() {
    private var device: Device? = null
    private var plugin by mutableStateOf<ControlPanelPlugin?>(null)
    private var volume by mutableStateOf<SystemVolumePlugin?>(null)
    private var displaySession by mutableStateOf<PanelSession?>(null)
    private val changed = Device.PluginsChangedListener { runOnUiThread { bindPlugins() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(DEVICE_ID)
        device = id?.let { KdeConnect.getInstance().getDevice(it) }
        if (device == null) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        bindPlugins()
        setContent {
            KdeTheme(this) {
                ControlPanelScreen(device?.name.orEmpty(), displaySession, plugin, volume, onBack = { finish() })
            }
        }
    }

    private fun bindPlugins() {
        plugin = device?.getPlugin<ControlPanelPlugin>()
        volume = device?.getPlugin<SystemVolumePlugin>()
        plugin?.session?.let { displaySession = it }
    }

    override fun onStart() {
        super.onStart()
        device?.addPluginsChangedListener(changed)
        bindPlugins()
    }

    override fun onStop() {
        device?.removePluginsChangedListener(changed)
        super.onStop()
    }

    companion object { const val DEVICE_ID = "deviceId" }
}

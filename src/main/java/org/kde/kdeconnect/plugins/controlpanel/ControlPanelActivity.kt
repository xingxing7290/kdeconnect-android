/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin
import org.kde.kdeconnect.ui.compose.KdeTheme

class ControlPanelActivity : AppCompatActivity() {
    private val idle = PanelIdleController(SystemClock::elapsedRealtime)
    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleTick = object : Runnable {
        override fun run() { idle.tick(); idleHandler.postDelayed(this, 200) }
    }
    private var device: Device? = null
    private var resumed = false
    private var windowFocused: Boolean? = null
    private var plugin by mutableStateOf<ControlPanelPlugin?>(null)
    private var volume by mutableStateOf<SystemVolumePlugin?>(null)
    private var displaySession by mutableStateOf<PanelSession?>(null)
    private val changed = Device.PluginsChangedListener { runOnUiThread { bindPlugins() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(DEVICE_ID)
        device = id?.let { KdeConnect.getInstance().getDevice(it) }
        if (device?.isPaired != true) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        bindPlugins()
        onBackPressedDispatcher.addCallback(this) { if (!idle.wake()) finish() }
        setContent {
            KdeTheme(this) {
                val state by (displaySession?.state ?: emptySession.state).collectAsState()
                val showing by idle.showing.collectAsState()
                LaunchedEffect(state.config?.idle) { idle.configure(state.config?.idle) }
                if (showing && state.config != null) {
                    PanelDialScreen(checkNotNull(state.config).idle, state.connection, onWake = { idle.wake() }, weather = state.weather)
                } else ControlPanelScreen(device?.name.orEmpty(), displaySession, plugin, volume,
                    onBack = { finish() }, assetCache = PanelAssetCache.get(this), onSynchronize = {
                        val current = plugin
                        if (current != null && device?.isReachable == true) current.synchronize()
                        else BackgroundService.ForceRefreshConnections(this)
                    })
            }
        }
    }

    private val emptySession = PanelSession()
    override fun onResume() {
        super.onResume()
        resumed = true
        idle.setActive(windowFocused != false)
        idleHandler.post(idleTick)
    }
    override fun onPause() {
        resumed = false
        idleHandler.removeCallbacks(idleTick)
        idle.setActive(false)
        super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocused = hasFocus
        idle.setActive(resumed && hasFocus)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val phase = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> PanelIdleController.Touch.START
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> PanelIdleController.Touch.END
            else -> PanelIdleController.Touch.CONTINUE
        }
        return idle.touch(phase) || super.dispatchTouchEvent(event)
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
            if (idle.key(event.keyCode, event.action == KeyEvent.ACTION_DOWN)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun bindPlugins() {
        val currentDevice = device
        if (currentDevice == null || !currentDevice.isPaired) {
            plugin = null
            volume = null
            displaySession = null
            finish()
            return
        }
        plugin = currentDevice.getPlugin<ControlPanelPlugin>()
        volume = currentDevice.getPlugin<SystemVolumePlugin>()
        displaySession = plugin?.session ?: displaySession ?: PanelDisplayCache.offline(this, currentDevice)
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

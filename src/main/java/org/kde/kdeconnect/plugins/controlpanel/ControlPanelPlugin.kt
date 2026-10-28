/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.JsonHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import org.kde.kdeconnect_tp.R

@LoadablePlugin
class ControlPanelPlugin : Plugin() {
    val session = PanelSession()
    private val handler = Handler(Looper.getMainLooper())
    private var alive = false
    private var syncAttempts = 0
    private val tick = object : Runnable {
        override fun run() {
            if (!alive) return
            session.expireRequests()
            if (session.state.value.connection == PanelConnection.SYNCING) {
                if (++syncAttempts <= 5) sendConfigRequest() else session.failed("sync_timeout")
            }
            handler.postDelayed(this, 2_000)
        }
    }

    override val displayName: String get() = context.getString(R.string.control_panel_title)
    override val description: String get() = context.getString(R.string.control_panel_description)
    override val settingsScope = SettingsScope.PER_DEVICE
    override val supportedPacketTypes = arrayOf(CONFIG, STATE, RESULT)
    override val outgoingPacketTypes = arrayOf(REQUEST)

    override fun getUiButtons() = listOf(
        PluginUiButton(displayName, R.drawable.run_command_plugin_icon_24dp) { activity ->
            activity.startActivity(Intent(activity, ControlPanelActivity::class.java).putExtra(ControlPanelActivity.DEVICE_ID, device.deviceId))
        }
    )

    override fun onCreate() {
        alive = true
        preferences.getString("panel_config", null)?.let(session::restoreDisplay)
        session.connected()
        syncAttempts = 0
        sendConfigRequest()
        handler.postDelayed(tick, 2_000)
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacks(tick)
        session.disconnected()
    }

    fun synchronize() {
        if (!alive || !device.isReachable) return
        session.retrySynchronization()
        syncAttempts = 0
        sendConfigRequest()
    }

    private fun sendConfigRequest() {
        val packet = request("config")
        send(packet)
    }

    private fun request(operation: String) = NetworkPacket(REQUEST).apply {
        this["protocolVersion"] = 1
        this["operation"] = operation
        this["sessionId"] = session.sessionId
    }

    private fun send(packet: NetworkPacket, requestId: String? = null) {
        device.sendPacket(packet, object : Device.SendPacketStatusCallback() {
            override fun onSuccess() = Unit
            override fun onFailure(e: Throwable) {
                if (!alive) return
                if (requestId != null) session.receiveResult(PanelResult(requestId, false, "send_failed"))
                else if (!device.isReachable) session.disconnected()
            }
        })
    }

    fun activate(buttonId: String): Boolean {
        if (!alive || !device.isReachable) return false
        val action = try { session.action(buttonId) } catch (_: IllegalArgumentException) { return false } catch (_: IllegalStateException) { return false }
        val packet = request("action").apply {
            this["requestId"] = action.requestId
            this["sequence"] = action.sequence
            this["revision"] = action.revision
            this["buttonId"] = action.buttonId
        }
        send(packet, action.requestId)
        return true
    }

    fun selectPage(pageId: String): Boolean {
        val current = session.state.value
        val config = current.config ?: return false
        if (!alive || !device.isReachable || current.connection != PanelConnection.READY || config.page(pageId) == null) return false
        if (config.mode == "single" && pageId != config.homePageId) return false
        send(request("page").apply { this["revision"] = config.revision; this["pageId"] = pageId })
        return true
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (!alive || !device.isPaired || np.type !in supportedPacketTypes || np.hasPayload()) return false
        // Access the wire body to reject type coercion in the generic NetworkPacket getters.
        val body = try {
            JsonHelper.json.parseToJsonElement(np.serialize()).let { (it as JsonObject)["body"] as JsonObject }
        } catch (_: IllegalArgumentException) { return false }
        if (number(body, "protocolVersion") != 1L) {
            session.failed("unsupported_version")
            return false
        }
        return when (np.type) {
            CONFIG -> {
                val config = body["config"] as? JsonObject ?: return false
                val accepted = session.receiveConfig(config.toString())
                if (accepted) {
                    syncAttempts = 0
                    preferences.edit().putString("panel_config", session.state.value.config?.encode()).apply()
                }
                accepted
            }
            STATE -> {
                val revision = number(body, "revision") ?: return false
                val page = string(body, "activePageId") ?: return false
                val foreground = string(body, "foregroundApplication") ?: return false
                session.receiveState(revision, page, foreground)
            }
            RESULT -> {
                val id = string(body, "requestId") ?: return false
                val successValue = body["success"] as? JsonPrimitive ?: return false
                if (successValue.isString) return false
                val success = successValue.booleanOrNull ?: return false
                val error = string(body, "error") ?: return false
                if (id.length > 64 || error.length > 120) return false
                if (id.isEmpty()) {
                    if (error == "stale_revision") synchronize()
                    else if (!success) session.failed(error)
                    true
                } else {
                    val accepted = session.receiveResult(PanelResult(id, success, error))
                    if (accepted && error == "stale_revision") synchronize()
                    accepted
                }
            }
            else -> false
        }
    }

    private fun number(body: JsonObject, key: String): Long? = (body[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
    private fun string(body: JsonObject, key: String): String? = (body[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        const val REQUEST = "kdeconnect.controlpanel.request"
        const val CONFIG = "kdeconnect.controlpanel.config"
        const val STATE = "kdeconnect.controlpanel.state"
        const val RESULT = "kdeconnect.controlpanel.result"
    }
}

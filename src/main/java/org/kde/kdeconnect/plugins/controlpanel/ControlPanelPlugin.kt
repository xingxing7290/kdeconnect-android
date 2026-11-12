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
import kotlinx.coroutines.flow.MutableStateFlow
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
    @Volatile private var alive = false
    @Volatile private var boundSessionId = ""
    val iconProblem = MutableStateFlow(false)
    private var assets: PanelAssetTransfers? = null
    private var syncAttempts = 0
    private var tick: Runnable? = null

    private fun withCurrentSession(id: String, change: () -> Boolean): Boolean = synchronized(session) {
        alive && session.sessionId == id && change()
    }

    private fun scheduleTick(id: String) {
        val scheduled = object : Runnable {
            override fun run() {
                var requestConfig = false
                if (!withCurrentSession(id) {
                    session.expireRequests()
                    if (session.state.value.connection == PanelConnection.SYNCING) {
                        if (++syncAttempts <= 5) requestConfig = true else session.failed("sync_timeout")
                    }
                    true
                }) return
                assets?.tick()
                if (requestConfig) sendConfigRequest(id)
                if (alive && session.sessionId == id) handler.postDelayed(this, 2_000)
            }
        }
        tick = scheduled
        handler.postDelayed(scheduled, 2_000)
    }

    override val displayName: String get() = context.getString(R.string.control_panel_title)
    override val description: String get() = context.getString(R.string.control_panel_description)
    override val settingsScope = SettingsScope.PER_DEVICE
    override val supportedPacketTypes = arrayOf(CONFIG, STATE, RESULT, ASSET, DIAL)
    override val outgoingPacketTypes = arrayOf(REQUEST)

    override fun getUiButtons() = listOf(
        PluginUiButton(displayName, R.drawable.run_command_plugin_icon_24dp) { activity ->
            activity.startActivity(Intent(activity, ControlPanelActivity::class.java).putExtra(ControlPanelActivity.DEVICE_ID, device.deviceId))
        }
    )

    override fun onCreate() {
        alive = true
        boundSessionId = ""
        assets = PanelAssetTransfers(PanelAssetCache.get(context), session, iconProblem, { id, revision ->
            send(request("asset").apply { this["assetId"] = id; this["revision"] = revision })
        }, ::synchronize)
        PanelDisplayCache.restore(preferences, session)
        session.connected()
        syncAttempts = 0
        sendConfigRequest()
        scheduleTick(session.sessionId)
    }

    override fun onDestroy() {
        alive = false
        boundSessionId = ""
        tick?.let(handler::removeCallbacks)
        tick = null
        assets?.stop()
        assets = null
        session.disconnected()
    }

    fun synchronize() {
        synchronizeSession(session.sessionId)
    }

    private fun synchronizeSession(id: String) {
        val started = withCurrentSession(id) {
            if (!canSend()) false else {
                session.retrySynchronization()
                syncAttempts = 0
                true
            }
        }
        if (started) sendConfigRequest(id)
    }

    private fun sendConfigRequest(id: String = session.sessionId) {
        val packet = request("config").apply { this["sessionId"] = id }
        send(packet)
    }

    private fun request(operation: String) = NetworkPacket(REQUEST).apply {
        this["protocolVersion"] = 1
        this["operation"] = operation
        this["sessionId"] = session.sessionId
    }

    private fun canSend(): Boolean {
        if (!alive) return false
        if (!device.isPaired || !device.isReachable) {
            session.disconnected()
            return false
        }
        return true
    }

    private fun send(packet: NetworkPacket, requestId: String? = null): Boolean = withCurrentSession(packet.getString("sessionId")) {
        if (!canSend()) return@withCurrentSession false
        val sendingSession = packet.getString("sessionId")
        device.sendPacket(packet, object : Device.SendPacketStatusCallback() {
            override fun onSuccess() = Unit
            override fun onFailure(e: Throwable) {
                withCurrentSession(sendingSession) {
                    if (!device.isPaired || !device.isReachable) session.disconnected()
                    else if (requestId != null) session.receiveResult(PanelResult(requestId, false, "send_failed"))
                    true
                }
            }
        })
        true
    }

    fun activate(buttonId: String): Boolean {
        if (!canSend()) return false
        val action = try { session.action(buttonId) } catch (_: IllegalArgumentException) { return false } catch (_: IllegalStateException) { return false }
        val packet = request("action").apply {
            this["sessionId"] = action.sessionId
            this["requestId"] = action.requestId
            this["sequence"] = action.sequence
            this["revision"] = action.revision
            this["buttonId"] = action.buttonId
        }
        return send(packet, action.requestId)
    }

    fun selectPage(pageId: String): Boolean = withCurrentSession(session.sessionId) {
        val current = session.state.value
        val config = current.config ?: return@withCurrentSession false
        if (!canSend() || current.connection != PanelConnection.READY || config.page(pageId) == null) return@withCurrentSession false
        if (config.mode == "single" && pageId != config.homePageId) return@withCurrentSession false
        send(request("page").apply { this["revision"] = config.revision; this["pageId"] = pageId })
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        val receivingSession = session.sessionId
        if (!alive || !device.isPaired || !device.isReachable || np.type !in supportedPacketTypes || (np.hasPayload() && np.type != ASSET)) {
            np.payload?.close(); return false
        }
        if (np.type == ASSET && np.serialize().length > 4096) { np.payload?.close(); return false }
        if (np.type == DIAL && np.serialize().length > 16_384) return false
        // Access the wire body to reject type coercion in the generic NetworkPacket getters.
        val body = try {
            JsonHelper.json.parseToJsonElement(np.serialize()).let { (it as JsonObject)["body"] as JsonObject }
        } catch (_: IllegalArgumentException) { np.payload?.close(); return false }
        val responseSession = string(body, "sessionId")
        if (("sessionId" in body && responseSession != receivingSession) ||
            (np.type != ASSET && "sessionId" !in body && boundSessionId == receivingSession)) {
            np.payload?.close(); return false
        }
        if (number(body, "protocolVersion") != 1L) {
            if (np.type == DIAL) return false
            if (np.type == ASSET) { np.payload?.close(); iconProblem.value = true; return false }
            withCurrentSession(receivingSession) { session.failed("unsupported_version"); true }
            return false
        }
        return when (np.type) {
            DIAL -> {
                val revision = number(body, "revision") ?: return false
                withCurrentSession(receivingSession) {
                    val accepted = session.receiveWeather(revision, body.toString())
                    if (accepted) preferences.edit().putString("panel_weather", session.state.value.weather?.encode()).apply()
                    accepted
                }
            }
            ASSET -> assets?.receive(body, np.payload) ?: false.also { np.payload?.close() }
            CONFIG -> {
                val config = body["config"] as? JsonObject ?: return false
                val accepted = withCurrentSession(receivingSession) {
                    if (!session.receiveConfig(config.toString())) false else {
                        if (responseSession != null) boundSessionId = responseSession
                        syncAttempts = 0
                        preferences.edit().putString("panel_config", session.state.value.config?.encode()).apply()
                        true
                    }
                }
                if (accepted && receivingSession == session.sessionId) {
                    session.state.value.config?.let { assets?.update(it) }
                }
                accepted
            }
            STATE -> {
                val revision = number(body, "revision") ?: return false
                val page = string(body, "activePageId") ?: return false
                val foreground = string(body, "foregroundApplication") ?: return false
                withCurrentSession(receivingSession) { session.receiveState(revision, page, foreground) }
            }
            RESULT -> {
                val id = string(body, "requestId") ?: return false
                val successValue = body["success"] as? JsonPrimitive ?: return false
                if (successValue.isString) return false
                val success = successValue.booleanOrNull ?: return false
                val error = string(body, "error") ?: return false
                if (id.length > 64 || error.length > 120 || success != error.isEmpty()) return false
                if (id.isEmpty()) {
                    if (error == "stale_revision") synchronizeSession(receivingSession)
                    withCurrentSession(receivingSession) {
                        if (!success && error != "stale_revision") session.failed(error)
                        true
                    }
                } else {
                    val accepted = withCurrentSession(receivingSession) { session.receiveResult(PanelResult(id, success, error)) }
                    if (accepted && error in setOf("stale_revision", "invalid_session")) synchronizeSession(receivingSession)
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
        const val ASSET = "kdeconnect.controlpanel.asset"
        const val DIAL = "kdeconnect.controlpanel.dial"
    }
}

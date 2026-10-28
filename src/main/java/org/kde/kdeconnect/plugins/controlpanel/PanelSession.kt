/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

enum class PanelConnection { DISCONNECTED, SYNCING, READY, ERROR }

data class PanelResult(val requestId: String, val success: Boolean, val error: String = "")

data class PanelSessionState(
    val connection: PanelConnection = PanelConnection.DISCONNECTED,
    val config: PanelConfig? = null,
    val activePageId: String = "",
    val foregroundApplication: String = "",
    val pendingButtons: Set<String> = emptySet(),
    val lastResult: PanelResult? = null,
    val error: String = "",
)

@Serializable
data class PanelActionRequest(
    val protocolVersion: Int = 1,
    val operation: String = "action",
    val requestId: String,
    val sessionId: String,
    val sequence: Long,
    val revision: Long,
    val buttonId: String,
)

/** One paired connection. No request survives a disconnect or config revision change. */
class PanelSession(
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
) {
    private data class Pending(val request: PanelActionRequest, val sentAt: Long)
    private val pending = LinkedHashMap<String, Pending>()
    private var hasSessionConfig = false
    private var nextSequence = 1L
    var sessionId: String = ""
        private set
    private val mutableState = MutableStateFlow(PanelSessionState())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun restoreDisplay(raw: String) {
        if (mutableState.value.connection != PanelConnection.DISCONNECTED) return
        val config = try { PanelConfig.parse(raw) } catch (_: IllegalArgumentException) { return }
        mutableState.value = mutableState.value.copy(config = config, activePageId = config.activePageId)
    }

    @Synchronized
    fun connected() {
        pending.clear()
        hasSessionConfig = false
        sessionId = UUID.randomUUID().toString()
        nextSequence = 1L
        mutableState.value = mutableState.value.copy(
            connection = PanelConnection.SYNCING, pendingButtons = emptySet(), lastResult = null, error = ""
        )
    }

    @Synchronized
    fun disconnected() {
        pending.clear()
        hasSessionConfig = false
        mutableState.value = mutableState.value.copy(
            connection = PanelConnection.DISCONNECTED, pendingButtons = emptySet(), lastResult = null, error = "offline"
        )
    }

    @Synchronized
    fun failed(error: String) {
        pending.clear()
        mutableState.value = mutableState.value.copy(connection = PanelConnection.ERROR, pendingButtons = emptySet(), error = error.take(120))
    }

    @Synchronized
    fun retrySynchronization() {
        if (mutableState.value.connection != PanelConnection.DISCONNECTED) {
            pending.clear()
            mutableState.value = mutableState.value.copy(connection = PanelConnection.SYNCING, pendingButtons = emptySet(), error = "")
        }
    }

    @Synchronized
    fun receiveConfig(raw: String): Boolean {
        val current = mutableState.value
        if (current.connection == PanelConnection.DISCONNECTED) return false
        val config = try {
            PanelConfig.parse(raw)
        } catch (_: IllegalArgumentException) {
            pending.clear()
            mutableState.value = current.copy(connection = PanelConnection.ERROR, pendingButtons = emptySet(), error = "invalid_config")
            return false
        }
        if (hasSessionConfig && current.config != null) {
            if (config.revision < current.config.revision) return false
            if (config.revision == current.config.revision && config != current.config) return false
        }
        val sameConfig = hasSessionConfig && config == current.config
        if (config != current.config || current.connection != PanelConnection.READY) pending.clear()
        hasSessionConfig = true
        mutableState.value = current.copy(
            connection = PanelConnection.READY,
            config = config,
            activePageId = when {
                config.mode == "single" -> config.homePageId
                sameConfig -> current.activePageId
                else -> config.activePageId
            },
            pendingButtons = pending.values.map { it.request.buttonId }.toSet(),
            error = "",
        )
        return true
    }

    @Synchronized
    fun receiveState(revision: Long, pageId: String, foregroundApplication: String): Boolean {
        val current = mutableState.value
        val config = current.config ?: return false
        if (current.connection != PanelConnection.READY || revision != config.revision || config.page(pageId) == null) return false
        if (foregroundApplication.length > 260) return false
        mutableState.value = current.copy(
            activePageId = if (config.mode == "single") config.homePageId else pageId,
            foregroundApplication = foregroundApplication,
        )
        return true
    }

    @Synchronized
    fun action(buttonId: String): PanelActionRequest {
        val current = mutableState.value
        check(current.connection == PanelConnection.READY) { "offline" }
        val config = checkNotNull(current.config)
        require(config.button(buttonId) != null) { "unknown_button" }
        check(buttonId !in current.pendingButtons && pending.size < 32) { "request_pending" }
        check(nextSequence <= PanelConfig.MAX_REVISION) { "sequence_exhausted" }
        val request = PanelActionRequest(
            requestId = newRequestId(), sessionId = sessionId, sequence = nextSequence++, revision = config.revision, buttonId = buttonId
        )
        check(request.requestId !in pending) { "duplicate_request_id" }
        pending[request.requestId] = Pending(request, elapsedMillis())
        mutableState.value = current.copy(pendingButtons = current.pendingButtons + buttonId, lastResult = null)
        return request
    }

    @Synchronized
    fun receiveResult(result: PanelResult): Boolean {
        if (pending.remove(result.requestId) == null) return false
        val stale = !result.success && result.error == "stale_revision"
        if (stale) pending.clear()
        mutableState.value = mutableState.value.copy(
            connection = if (stale) PanelConnection.SYNCING else mutableState.value.connection,
            pendingButtons = pending.values.map { it.request.buttonId }.toSet(),
            lastResult = result,
            error = if (stale) "stale_revision" else "",
        )
        return true
    }

    @Synchronized
    fun expireRequests(): List<String> {
        val now = elapsedMillis()
        val expired = pending.filterValues { now - it.sentAt >= REQUEST_TIMEOUT_MS }.keys.toList()
        expired.forEach(pending::remove)
        if (expired.isNotEmpty()) {
            mutableState.value = mutableState.value.copy(
                pendingButtons = pending.values.map { it.request.buttonId }.toSet(),
                lastResult = PanelResult(expired.last(), false, "confirmation_timeout"),
            )
        }
        return expired
    }

    companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
    }
}

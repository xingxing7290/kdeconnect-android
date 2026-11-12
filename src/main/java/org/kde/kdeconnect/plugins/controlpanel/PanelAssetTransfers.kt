/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.kde.kdeconnect.NetworkPacket

class PanelAssetTransfers(
    private val cache: PanelAssetCache,
    private val session: PanelSession,
    private val problem: MutableStateFlow<Boolean>,
    private val send: (String, Long) -> Unit,
    private val resynchronize: () -> Unit,
) {
    private data class Pending(val id: String, val revision: Long, val sessionId: String, val started: Long)
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private val attempts = mutableMapOf<String, Int>()
    private val failed = mutableSetOf<String>()
    private var pending: Pending? = null
    private var receiving = false
    @Volatile private var alive = true

    @Synchronized fun update(config: PanelConfig) {
        pending = null
        queue.clear(); attempts.clear(); failed.clear(); problem.value = false
        config.pages.flatMap { it.slots }.mapNotNull { it?.icon?.takeIf(String::isNotEmpty) }.distinct().forEach(queue::addLast)
        pump()
    }
    @Synchronized fun tick() {
        val current = pending
        if (!receiving && current != null && SystemClock.elapsedRealtime() - current.started >= 20_000) finish(current, false)
        pump()
    }
    @Synchronized private fun pump() {
        if (!alive || receiving || pending != null || session.state.value.connection != PanelConnection.READY) return
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (cache.contains(id)) { failed.remove(id); continue }
            val config = session.state.value.config ?: return
            pending = Pending(id, config.revision, session.sessionId, SystemClock.elapsedRealtime())
            attempts[id] = (attempts[id] ?: 0) + 1
            send(id, config.revision)
            return
        }
        problem.value = failed.isNotEmpty()
    }
    @Synchronized fun receive(body: JsonObject, payload: NetworkPacket.Payload?): Boolean {
        fun string(key: String) = (body[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun number(key: String) = (body[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
        val request = pending
        if (!alive || receiving || request == null || string("assetId") != request.id || string("sessionId") != request.sessionId || number("revision") != request.revision) {
            payload?.close(); return false
        }
        val error = string("error")
        if (!error.isNullOrEmpty()) {
            payload?.close()
            finish(request, false)
            if (error == "stale_revision") handler.post(resynchronize)
            return true
        }
        val size = number("size")
        if (string("mime") != "image/png" || size == null || size !in 1..PanelAssetCache.MAX_BYTES.toLong() || payload == null || payload.payloadSize != size) {
            payload?.close(); finish(request, false); return false
        }
        receiving = true
        IO.execute {
            val accepted = try {
                payload.setReadTimeout(3000)
                val input = payload.inputStream ?: throw java.io.IOException("missing_payload")
                val deadline = SystemClock.elapsedRealtime() + 15_000
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size().toLong() < size) {
                    if (!alive || SystemClock.elapsedRealtime() > deadline) throw java.io.IOException("asset_timeout")
                    val count = input.read(buffer, 0, minOf(buffer.size.toLong(), size - output.size()).toInt())
                    if (count <= 0) throw java.io.IOException("incomplete_payload")
                    output.write(buffer, 0, count)
                }
                alive && cache.accept(request.id, output.toByteArray())
            } catch (_: Exception) { false } finally { payload.close() }
            handler.post { completed(request, accepted) }
        }
        return true
    }
    @Synchronized private fun completed(request: Pending, accepted: Boolean) {
        receiving = false
        if (pending == request) finish(request, accepted)
        else handler.postDelayed({ pump() }, 100)
    }
    @Synchronized private fun finish(request: Pending, accepted: Boolean) {
        if (pending != request) return
        pending = null
        if (accepted) failed.remove(request.id)
        else if ((attempts[request.id] ?: 0) < 2) queue.addLast(request.id)
        else failed.add(request.id)
        problem.value = failed.isNotEmpty()
        handler.postDelayed({ pump() }, if (accepted) 100 else 1000)
    }
    @Synchronized fun stop() {
        alive = false; pending = null; queue.clear()
        handler.removeCallbacksAndMessages(null)
    }
    companion object {
        private val IO = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "ControlPanelAssets").apply { isDaemon = true } }
    }
}

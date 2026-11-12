/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.kde.kdeconnect.NetworkPacket
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PanelAssetTransfersTest {
    @get:Rule val temporary = TemporaryFolder()
    private val id = "a".repeat(64)
    private val session = PanelSession()
    private val problem = MutableStateFlow(false)
    private val sent = mutableListOf<Pair<String, Long>>()
    private var synchronized = 0
    private lateinit var transfers: PanelAssetTransfers
    private lateinit var config: PanelConfig

    @Before fun setup() {
        val base = PanelConfig.parse(File("docs/control-panel/fixtures/config-v1.json").readText())
        val first = base.pages.first()
        val slots = first.slots.toMutableList().apply { this[0] = checkNotNull(this[0]).copy(icon = id) }
        config = base.copy(pages = listOf(first.copy(slots = slots)) + base.pages.drop(1))
        session.connected()
        assertTrue(session.receiveConfig(config.encode()))
        transfers = PanelAssetTransfers(PanelAssetCache(temporary.newFolder()), session, problem, { icon, revision ->
            sent.add(icon to revision)
        }, { synchronized++ })
        transfers.update(config)
    }
    @After fun cleanup() { transfers.stop() }
    private fun body(error: String = "", sessionId: String = session.sessionId): JsonObject = buildJsonObject {
        put("assetId", id); put("sessionId", sessionId); put("revision", config.revision)
        put("mime", "image/png"); put("size", 1)
        if (error.isNotEmpty()) put("error", error)
    }
    @Test fun missingAssetsHaveBoundedRetriesAndDoNotDisableActions() {
        assertEquals(listOf(id to config.revision), sent)
        assertTrue(transfers.receive(body("asset_missing"), null))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(2, sent.size)
        assertTrue(transfers.receive(body("asset_missing"), null))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        transfers.tick()
        assertEquals(2, sent.size)
        assertTrue(problem.value)
        assertEquals(PanelConnection.READY, session.state.value.connection)
        assertEquals("docs", session.action("docs").buttonId)
        transfers.update(config)
        assertEquals(3, sent.size)
        assertFalse(problem.value)
    }
    @Test fun wrongSessionAndSizeClosePayloadWithoutAcceptingIt() {
        class TrackingInput : ByteArrayInputStream(byteArrayOf(1)) {
            var closed = false
            override fun close() { closed = true; super.close() }
        }
        val stale = TrackingInput()
        assertFalse(transfers.receive(body(sessionId = "wrong"), NetworkPacket.Payload(stale, 1)))
        assertTrue(stale.closed)
        val mismatch = TrackingInput()
        assertFalse(transfers.receive(body(), NetworkPacket.Payload(mismatch, 2)))
        assertTrue(mismatch.closed)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(2, sent.size)
        transfers.stop()
        val late = TrackingInput()
        assertFalse(transfers.receive(body(), NetworkPacket.Payload(late, 1)))
        assertTrue(late.closed)
        assertEquals(PanelConnection.READY, session.state.value.connection)
    }
    @Test fun timeoutRetriesAndStaleRevisionRequestsConfiguration() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        transfers.tick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(2, sent.size)
        assertTrue(transfers.receive(body("stale_revision"), null))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, synchronized)
        assertTrue(problem.value)
        transfers.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
        assertEquals(2, sent.size)
    }
    @Test fun readTimeoutOnlyChangesTheSelectedPayloadSocket() {
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            java.net.Socket(server.inetAddress, server.localPort).use { client ->
                server.accept().use {
                    java.net.Socket().use { untouched ->
                        NetworkPacket.Payload(client, 1).use { payload ->
                            assertEquals(0, client.soTimeout)
                            assertEquals(0, untouched.soTimeout)
                            payload.setReadTimeout(3000)
                            assertEquals(3000, client.soTimeout)
                            assertEquals(0, untouched.soTimeout)
                            assertTrue(runCatching { payload.setReadTimeout(0) }.isFailure)
                            assertTrue(runCatching { payload.setReadTimeout(60_001) }.isFailure)
                            assertEquals(3000, client.soTimeout)
                        }
                    }
                }
            }
        }
    }
}

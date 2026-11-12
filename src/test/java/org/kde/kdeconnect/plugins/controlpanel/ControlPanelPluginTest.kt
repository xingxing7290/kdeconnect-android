/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.JsonHelper
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class ControlPanelPluginTest {
    private fun withIcon(id: String): NetworkPacket {
        val base = config()
        val page = base.pages.first()
        val slots = page.slots.toMutableList().apply { this[0] = checkNotNull(this[0]).copy(icon = id) }
        val updated = base.copy(pages = listOf(page.copy(slots = slots)) + base.pages.drop(1))
        return NetworkPacket(ControlPanelPlugin.CONFIG).apply {
            this["protocolVersion"] = 1
            this["config"] = JsonHelper.json.parseToJsonElement(updated.encode()) as JsonObject
        }
    }
    private fun asset(id: String, bytes: ByteArray) = NetworkPacket(ControlPanelPlugin.ASSET).apply {
        this["protocolVersion"] = 1; this["sessionId"] = plugin.session.sessionId; this["revision"] = 7L
        this["assetId"] = id; this["mime"] = "image/png"; this["size"] = bytes.size.toLong()
        payload = NetworkPacket.Payload(bytes)
    }
    @Test fun referencedAssetIsRequestedVerifiedAndCached() {
        val bitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
        val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val id = PanelAssetCache.hash(bytes)
        val context = ApplicationProvider.getApplicationContext<Application>()
        java.io.File(context.filesDir, "control-panel-assets/$id.png").delete()
        plugin.onCreate()
        assertTrue(plugin.onPacketReceived(withIcon(id)))
        assertEquals("asset", sent.last().getString("operation"))
        assertTrue(plugin.onPacketReceived(asset(id, bytes)))
        val cache = PanelAssetCache.get(context)
        val deadline = System.currentTimeMillis() + 3000
        while (!cache.contains(id) && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(cache.contains(id))
        assertNotNull(cache.bitmap(id))
        assertEquals(PanelConnection.READY, plugin.session.state.value.connection)
    }
    @Test fun assetFailuresNeverDisableConfiguredActions() {
        val id = "a".repeat(64)
        plugin.onCreate()
        assertTrue(plugin.onPacketReceived(withIcon(id)))
        val stale = asset(id, byteArrayOf(1)).apply { this["sessionId"] = UUID.randomUUID().toString() }
        assertFalse(plugin.onPacketReceived(stale))
        val missing = NetworkPacket(ControlPanelPlugin.ASSET).apply {
            this["protocolVersion"] = 1; this["sessionId"] = plugin.session.sessionId; this["revision"] = 7L
            this["assetId"] = id; this["size"] = 0; this["error"] = "asset_missing"
        }
        assertTrue(plugin.onPacketReceived(missing))
        assertEquals(PanelConnection.READY, plugin.session.state.value.connection)
        assertTrue(plugin.activate("docs"))
    }
    private lateinit var plugin: ControlPanelPlugin
    private val sent = mutableListOf<NetworkPacket>()

    @Before
    fun setup() {
        val device = mockk<Device> {
            every { deviceId } returns "panel-test-device"
            every { isPaired } returns true
            every { isReachable } returns true
            every { sendPacket(any(), any()) } answers {
                sent.add(arg(0))
                arg<Device.SendPacketStatusCallback>(1).onSuccess()
            }
        }
        plugin = ControlPanelPlugin().apply {
            setContext(ApplicationProvider.getApplicationContext<Application>(), device)
            preferences.edit().clear().commit()
        }
    }

    @After fun cleanup() { plugin.onDestroy(); sent.clear() }

    private fun config() = PanelConfig.parse(File("docs/control-panel/fixtures/config-v1.json").readText())
    private fun configurationPacket() = NetworkPacket(ControlPanelPlugin.CONFIG).apply {
        this["protocolVersion"] = 1
        this["config"] = JsonHelper.json.parseToJsonElement(config().encode()) as JsonObject
    }
    private fun result(id: String, error: String = "") = NetworkPacket(ControlPanelPlugin.RESULT).apply {
        this["protocolVersion"] = 1
        this["requestId"] = id
        this["success"] = error.isEmpty()
        this["error"] = error
    }
    private fun ready() {
        plugin.onCreate()
        assertTrue(plugin.onPacketReceived(configurationPacket()))
    }

    @Test fun handshakeEstablishesSessionAndDisconnectedClicksAreNotSent() {
        assertFalse(plugin.activate("docs"))
        assertTrue(sent.isEmpty())
        plugin.onCreate()
        assertEquals(ControlPanelPlugin.REQUEST, sent.single().type)
        assertEquals("config", sent.single().getString("operation"))
        assertEquals(UUID.fromString(plugin.session.sessionId).toString(), sent.single().getString("sessionId"))
        assertFalse(plugin.activate("docs"))
        plugin.onDestroy()
        assertFalse(plugin.activate("docs"))
        assertEquals(1, sent.size)
    }

    @Test fun actionContainsOnlyConfiguredIdentityAndWaitsForAcknowledgement() {
        ready()
        assertTrue(plugin.activate("docs"))
        val action = sent.last()
        assertEquals("action", action.getString("operation"))
        assertEquals(7L, action.getLong("revision"))
        assertEquals(1L, action.getLong("sequence"))
        assertFalse(action.has("target"))
        assertFalse(action.has("path"))
        assertFalse(plugin.activate("docs"))
        assertTrue(plugin.onPacketReceived(result(action.getString("requestId"))))
        assertTrue(plugin.activate("docs"))
        assertEquals(2L, sent.last().getLong("sequence"))
        assertFalse(plugin.onPacketReceived(result("unknown-request")))
    }

    @Test fun staleResultResynchronizesWithoutResettingSessionOrSequence() {
        ready()
        val session = plugin.session.sessionId
        assertTrue(plugin.activate("docs"))
        val id = sent.last().getString("requestId")
        assertTrue(plugin.onPacketReceived(result(id, "stale_revision")))
        assertEquals(PanelConnection.SYNCING, plugin.session.state.value.connection)
        assertEquals("config", sent.last().getString("operation"))
        assertEquals(session, sent.last().getString("sessionId"))
        assertFalse(plugin.activate("docs"))
        assertTrue(plugin.onPacketReceived(configurationPacket()))
        assertTrue(plugin.activate("docs"))
        assertEquals(2L, sent.last().getLong("sequence"))
    }

    @Test fun reconnectRestoresDisplayButRequiresFreshSynchronization() {
        ready()
        val previous = plugin.session.sessionId
        assertTrue(plugin.activate("docs"))
        plugin.onDestroy()
        plugin.onCreate()
        assertNotEquals(previous, plugin.session.sessionId)
        assertEquals(config(), plugin.session.state.value.config)
        assertEquals(PanelConnection.SYNCING, plugin.session.state.value.connection)
        assertTrue(plugin.session.state.value.pendingButtons.isEmpty())
        assertFalse(plugin.activate("docs"))
        plugin.onPacketReceived(configurationPacket())
        assertTrue(plugin.activate("docs"))
        assertEquals(1L, sent.last().getLong("sequence"))
    }

    @Test fun malformedVersionAndBooleansAreNotCoerced() {
        ready()
        assertTrue(plugin.activate("docs"))
        val response = result(sent.last().getString("requestId"))
        response["success"] = "true"
        assertFalse(plugin.onPacketReceived(response))
        assertTrue(plugin.session.state.value.pendingButtons.contains("docs"))
        val version = configurationPacket().apply { this["protocolVersion"] = "1" }
        assertFalse(plugin.onPacketReceived(version))
        assertEquals(PanelConnection.ERROR, plugin.session.state.value.connection)
        assertFalse(plugin.activate("docs"))
    }

    @Test fun runtimePagesRequireMatchingRevisionAndFailuresDisableControls() {
        ready()
        assertTrue(plugin.selectPage("editor"))
        assertEquals("page", sent.last().getString("operation"))
        assertFalse(plugin.selectPage("unknown"))
        val state = NetworkPacket(ControlPanelPlugin.STATE).apply {
            this["protocolVersion"] = 1
            this["revision"] = 6L
            this["activePageId"] = "editor"
            this["foregroundApplication"] = "notepad.exe"
        }
        assertFalse(plugin.onPacketReceived(state))
        state["revision"] = 7L
        assertTrue(plugin.onPacketReceived(state))
        assertEquals("editor", plugin.session.state.value.activePageId)
        assertTrue(plugin.onPacketReceived(result("", "config_unavailable")))
        assertFalse(plugin.activate("docs"))
        plugin.synchronize()
        assertEquals(PanelConnection.SYNCING, plugin.session.state.value.connection)
    }
}

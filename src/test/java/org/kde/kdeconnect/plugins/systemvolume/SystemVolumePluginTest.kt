package org.kde.kdeconnect.plugins.systemvolume

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket

@RunWith(AndroidJUnit4::class)
class SystemVolumePluginTest {
    private fun outputPacket(default: String = "one") = NetworkPacket("kdeconnect.systemvolume").apply {
        set("sinkList", buildJsonArray {
            listOf("one", "two").forEach { id ->
                addJsonObject {
                    put("name", id); put("description", "Same friendly name"); put("volume", if (id == "one") 23 else 42)
                    put("maxVolume", 100); put("muted", false); put("enabled", id == default)
                }
            }
        })
    }
    @Test fun requestingFreshSinksUsesExistingProtocol() {
        systemVolumePlugin.requestSinks()
        assertEquals("kdeconnect.systemvolume.request", packet?.type)
        assertTrue(checkNotNull(packet).getBoolean("requestSinks"))
    }
    @Test fun defaultChangesAndRemovalDoNotSelectArbitraryDevice() {
        systemVolumePlugin.onPacketReceived(outputPacket())
        assertEquals("one", getDefaultSink(systemVolumePlugin)?.name)
        assertEquals(2, systemVolumePlugin.sinks.size)
        systemVolumePlugin.onPacketReceived(NetworkPacket("kdeconnect.systemvolume").apply {
            set("name", "two"); set("enabled", true)
        })
        assertEquals("two", getDefaultSink(systemVolumePlugin)?.name)
        assertEquals(1, systemVolumePlugin.sinks.count { it.isDefault })
        systemVolumePlugin.onPacketReceived(outputPacket(default = "missing"))
        assertEquals(null, getDefaultSink(systemVolumePlugin))
        systemVolumePlugin.onPacketReceived(NetworkPacket("kdeconnect.systemvolume").apply { set("sinkList", buildJsonArray {}) })
        assertTrue(systemVolumePlugin.sinks.isEmpty())
    }
    @Test fun listListenersCanRemoveThemselvesDuringNotification() {
        var firstCalls = 0
        var secondCalls = 0
        val first = object : SystemVolumePlugin.SinkListener {
            override fun sinksChanged() { firstCalls++; systemVolumePlugin.removeSinkListener(this) }
        }
        val second = object : SystemVolumePlugin.SinkListener {
            override fun sinksChanged() { secondCalls++ }
        }
        systemVolumePlugin.addSinkListener(first)
        systemVolumePlugin.addSinkListener(second)
        systemVolumePlugin.onPacketReceived(outputPacket())
        systemVolumePlugin.onPacketReceived(outputPacket())
        assertEquals(1, firstCalls)
        assertEquals(2, secondCalls)
    }
    @Test fun sinkListenersCanRemoveThemselvesDuringNotification() {
        systemVolumePlugin.onPacketReceived(outputPacket())
        val sink = systemVolumePlugin.sinks.first()
        var calls = 0
        val listener = object : Sink.UpdateListener {
            override fun updateSink(sink: Sink) { calls++; sink.removeListener(this) }
        }
        sink.addListener(listener)
        sink.setVolume(10)
        sink.setMute(true)
        assertEquals(1, calls)
    }
    private lateinit var systemVolumePlugin: SystemVolumePlugin
    private lateinit var context: Context
    private lateinit var device: Device
    private var packet: NetworkPacket? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        device = mockk {
            every { sendPacket(any()) } answers {
                val sentPacket = arg<NetworkPacket>(0)
                packet = sentPacket
            }
        }
        systemVolumePlugin = SystemVolumePlugin().apply {
            setContext(context, device)
        }
    }

    @After
    fun cleanup() {
        packet = null // Ensuring we clean up any captured packets
    }

    // LOCAL -> REMOTE

    @Test
    fun testSendVolume() {
        systemVolumePlugin.sendVolume("Sink 1", 85)
        val sentPacket = checkNotNull(packet)

        assertEquals("kdeconnect.systemvolume.request", sentPacket.type)
        assertEquals(85, sentPacket.getInt("volume"))
        assertEquals("Sink 1", sentPacket.getString("name"))
    }

    @Test
    fun testSendMute() {
        systemVolumePlugin.sendMute("Sink 1", true)
        val sentPacket = checkNotNull(packet)

        assertEquals("kdeconnect.systemvolume.request", sentPacket.type)
        assertTrue(sentPacket.getBoolean("muted"))
        assertEquals("Sink 1", sentPacket.getString("name"))
    }

    @Test
    fun testSendEnable() {
        systemVolumePlugin.sendEnable("Sink 1")
        val sentPacket = checkNotNull(packet)

        assertEquals("kdeconnect.systemvolume.request", sentPacket.type)
        assertEquals(true, sentPacket.getBoolean("enabled"))
        assertEquals("Sink 1", sentPacket.getString("name"))
    }

    // REMOTE -> LOCAL

    @Test
    fun testSinkListeners() {
        val listener = mockk<SystemVolumePlugin.SinkListener>(relaxed = true)

        systemVolumePlugin.addSinkListener(listener)
        systemVolumePlugin.addSinkListener(listener)

        // Simulate receiving a packet to trigger listener
        val sinkPacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("sinkList", listOf())
        }

        systemVolumePlugin.onPacketReceived(sinkPacket)

        // Verify listener called
        verify(exactly = 2) { listener.sinksChanged() }
    }

    @Test
    fun testReceiveSinkList() {
        // Simulate receiving a packet with sink list
        val sinkPacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("sinkList", buildJsonArray {
                addJsonObject {
                    put("name", "Sink 1")
                    put("volume", 50)
                    put("muted", false)
                    put("description", "")
                    put("maxVolume", 100)
                }
                addJsonObject {
                    put("name", "Sink 2")
                    put("volume", 70)
                    put("muted", true)
                    put("description", "")
                    put("maxVolume", 100)
                }
            })
        }

        assertTrue(systemVolumePlugin.onPacketReceived(sinkPacket))

        val sinks = systemVolumePlugin.sinks
        assertEquals(2, sinks.size)

        val sink1 = sinks.first { it.name == "Sink 1" }
        assertEquals(50, sink1.volume)
        assertTrue(!sink1.isMute())

        val sink2 = sinks.first { it.name == "Sink 2" }
        assertEquals(70, sink2.volume)
        assertTrue(sink2.isMute())
    }

    @Test
    fun testReceiveSinkUpdate() {
        // First, add a sink to ensure proper updates
        val sinkPacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("sinkList", buildJsonArray {
                addJsonObject {
                    put("name", "Sink 1")
                    put("volume", 30)
                    put("muted", false)
                    put("description", "")
                    put("maxVolume", 100)
                }
            })
        }

        systemVolumePlugin.onPacketReceived(sinkPacket)

        // Update the sink's volume and mute status
        val updatePacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("name", "Sink 1")
            set("volume", 40)
            set("muted", true)
        }

        assertTrue(systemVolumePlugin.onPacketReceived(updatePacket))

        val sinks = systemVolumePlugin.sinks
        val updatedSink = sinks.first { it.name == "Sink 1" }

        assertEquals(40, updatedSink.volume)
        assertEquals(true, updatedSink.isMute())
        assertTrue(updatedSink.isMute())
    }

    @Test
    fun testReceiveSinkListFloatVolumeAndInvalidSink() {
        val sinkPacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("sinkList", buildJsonArray {
                addJsonObject {
                    put("name", "Bad sink")
                    put("volume", "loud")
                    put("muted", false)
                    put("description", "")
                    put("maxVolume", 100)
                }
                addJsonObject {
                    // The macOS desktop sends the volume as a float
                    put("name", "macOS sink")
                    put("volume", 36.99999809265137)
                    put("muted", true)
                    put("description", "")
                    put("maxVolume", 100)
                    put("enabled", true)
                }
            })
        }

        assertTrue(systemVolumePlugin.onPacketReceived(sinkPacket))

        val sinks = systemVolumePlugin.sinks
        assertEquals(1, sinks.size)
        val sink = sinks.first()
        assertEquals("macOS sink", sink.name)
        assertEquals(37, sink.volume)
        assertTrue(sink.isMute())
        assertTrue(sink.isDefault)
    }
}

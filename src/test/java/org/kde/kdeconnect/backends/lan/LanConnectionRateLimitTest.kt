/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.backends.lan

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.net.InetAddress
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.TrustedDevices
import org.kde.kdeconnect.helpers.TrustedNetworkHelper

@RunWith(AndroidJUnit4::class)
class LanConnectionRateLimitTest {
    private val remote = "0123456789abcdef0123456789abcdef"
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var provider: LanLinkProvider

    @Before fun setup() {
        mockkObject(DeviceHelper, TrustedDevices, TrustedNetworkHelper)
        every { DeviceHelper.getDeviceId(any()) } returns "fedcba9876543210fedcba9876543210"
        every { TrustedDevices.isTrustedDevice(any(), remote) } returns false
        every { TrustedNetworkHelper.isTrustedNetwork(any()) } returns true
        provider = LanLinkProvider(context)
    }
    @After fun cleanup() { unmockkObject(DeviceHelper, TrustedDevices, TrustedNetworkHelper) }

    private fun identity() = NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY).apply {
        this["deviceId"] = remote
        this["deviceName"] = "Multi-interface peer"
        this["deviceType"] = "desktop"
        this["protocolVersion"] = 8
        this["incomingCapabilities"] = emptyList<String>()
        this["outgoingCapabilities"] = emptyList<String>()
    }.serialize()

    @Test fun anIdentityFromAnUnreachableAddressDoesNotConsumeTheConnectedPeerBudget() {
        assertNotNull(provider.unserializeReceivedIdentityPacket(identity()))
        assertNotNull(provider.unserializeReceivedIdentityPacket(identity()))
        assertFalse(provider.rateLimitByDeviceId(remote))
        assertTrue(provider.rateLimitByDeviceId(remote))
    }

    @Test fun addressRateLimitStillBlocksRepeatedPacketsButAllowsOtherInterfaces() {
        val first = InetAddress.getByName("192.168.10.3")
        val second = InetAddress.getByName("192.168.119.245")
        assertFalse(provider.rateLimitByIp(first))
        assertTrue(provider.rateLimitByIp(first))
        assertFalse(provider.rateLimitByIp(second))
    }

    @Test fun untrustedNetworkStillRejectsUnknownDevices() {
        every { TrustedNetworkHelper.isTrustedNetwork(any()) } returns false
        assertNull(provider.unserializeReceivedIdentityPacket(identity()))
        every { TrustedDevices.isTrustedDevice(any(), remote) } returns true
        assertNotNull(provider.unserializeReceivedIdentityPacket(identity()))
    }
}

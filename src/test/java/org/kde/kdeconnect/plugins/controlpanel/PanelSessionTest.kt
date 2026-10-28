/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PanelSessionTest {
    private fun fixture() = PanelConfig.parse(File("docs/control-panel/fixtures/config-v1.json").readText())

    private fun readySession(): PanelSession = PanelSession().apply {
        connected()
        assertTrue(receiveConfig(fixture().encode()))
    }

    @Test
    fun noClickCanBeQueuedWhileOfflineOrSyncing() {
        val session = PanelSession()
        assertThrows(IllegalStateException::class.java) { session.action("docs") }
        session.connected()
        assertThrows(IllegalStateException::class.java) { session.action("docs") }
        assertTrue(session.receiveConfig(fixture().encode()))
        val request = session.action("docs")
        session.disconnected()
        assertThrows(IllegalStateException::class.java) { session.action("docs") }
        assertFalse(session.receiveResult(PanelResult(request.requestId, true)))
        session.connected()
        assertTrue(session.state.value.pendingButtons.isEmpty())
        assertThrows(IllegalStateException::class.java) { session.action("docs") }
    }

    @Test
    fun duplicateTapWaitsForConfirmationAndUnknownResultsAreIgnored() {
        val session = readySession()
        val request = session.action("docs")
        assertEquals(7L, request.revision)
        assertThrows(IllegalStateException::class.java) { session.action("docs") }
        assertThrows(IllegalArgumentException::class.java) { session.action("missing") }
        assertFalse(session.receiveResult(PanelResult("other-request", true)))
        assertTrue(session.receiveResult(PanelResult(request.requestId, true)))
        assertFalse(session.receiveResult(PanelResult(request.requestId, true)))
        assertTrue(session.state.value.pendingButtons.isEmpty())
    }

    @Test
    fun staleResultRequiresFreshConfiguration() {
        val session = readySession()
        val request = session.action("docs")
        assertTrue(session.receiveResult(PanelResult(request.requestId, false, "stale_revision")))
        assertEquals(PanelConnection.SYNCING, session.state.value.connection)
        assertThrows(IllegalStateException::class.java) { session.action("docs") }
        assertTrue(session.receiveConfig(fixture().copy(revision = 8).encode()))
        assertEquals(8L, session.action("docs").revision)
    }

    @Test
    fun outOfOrderPacketsDoNotReplaceCurrentConfiguration() {
        val session = readySession()
        assertFalse(session.receiveConfig(fixture().copy(revision = 6).encode()))
        assertFalse(session.receiveConfig(fixture().copy(mode = "follow").encode()))
        assertFalse(session.receiveState(6, "editor", "notepad.exe"))
        assertFalse(session.receiveState(7, "missing", "notepad.exe"))
        assertTrue(session.receiveState(7, "editor", "notepad.exe"))
        assertEquals("editor", session.state.value.activePageId)
        assertTrue(session.receiveConfig(fixture().encode()))
        assertEquals("editor", session.state.value.activePageId)
        assertFalse(session.receiveConfig("{broken"))
        assertFalse(session.receiveConfig(fixture().copy(revision = 6).encode()))
    }

    @Test
    fun timeoutsReleasePendingButtonWithoutRetrying() {
        var clock = 0L
        val session = PanelSession(elapsedMillis = { clock }, newRequestId = { "request-1" })
        session.connected()
        session.receiveConfig(fixture().encode())
        val request = session.action("docs")
        clock = PanelSession.REQUEST_TIMEOUT_MS - 1
        assertTrue(session.expireRequests().isEmpty())
        clock++
        assertEquals(listOf(request.requestId), session.expireRequests())
        assertTrue(session.state.value.pendingButtons.isEmpty())
        assertEquals("confirmation_timeout", session.state.value.lastResult?.error)
        assertFalse(session.receiveResult(PanelResult(request.requestId, true)))
    }

    @Test
    fun malformedConfigurationDisablesActionsButPreservesLastDisplay() {
        val session = readySession()
        assertFalse(session.receiveConfig("{broken"))
        assertEquals(PanelConnection.ERROR, session.state.value.connection)
        assertEquals(fixture(), session.state.value.config)
        assertThrows(IllegalStateException::class.java) { session.action("docs") }
    }
}

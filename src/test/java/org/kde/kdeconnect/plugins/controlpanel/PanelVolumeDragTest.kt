/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import org.junit.Assert.*
import org.junit.Test

class PanelVolumeDragTest {
    @Test fun draggingIsRateLimitedAndFinalValueIsAlwaysSent() {
        var now = 0L
        val sent = mutableListOf<Pair<String, Int>>()
        val drag = PanelVolumeDrag({ now }) { id, volume -> sent.add(id to volume) }
        assertEquals(10, drag.change("one", 10, 100, true))
        now = 30
        drag.change("one", 20, 100, true)
        assertEquals(listOf("one" to 10), sent)
        now = 80
        drag.change("one", 30, 100, true)
        now = 90
        drag.change("one", 40, 100, true)
        drag.finish("one", true)
        assertEquals(listOf("one" to 10, "one" to 30, "one" to 40), sent)
    }
    @Test fun changingDefaultDuringGestureCannotWriteToNewOutput() {
        val sent = mutableListOf<Pair<String, Int>>()
        val drag = PanelVolumeDrag({ 0L }) { id, volume -> sent.add(id to volume) }
        drag.change("one", 10, 100, true)
        assertNull(drag.change("two", 70, 100, true))
        drag.finish("two", true)
        assertEquals(listOf("one" to 10), sent)
        drag.change("two", 15, 100, true)
        drag.finish("two", true)
        assertEquals("two" to 15, sent.last())
    }
    @Test fun offlineOrMissingOutputsDoNotQueueFinalWrites() {
        val sent = mutableListOf<Pair<String, Int>>()
        val drag = PanelVolumeDrag({ 0L }) { id, volume -> sent.add(id to volume) }
        assertNull(drag.change(null, 20, 100, true))
        assertNull(drag.change("one", 20, 0, true))
        assertNull(drag.change("one", 20, 100, false))
        assertTrue(sent.isEmpty())
        drag.change("one", 10, 100, true)
        drag.finish("one", false)
        assertEquals(1, sent.size)
        drag.finish("one", true)
        assertEquals(1, sent.size)
    }
    @Test fun valuesAreClampedAndClockRollbackRestartsSending() {
        var now = 100L
        val sent = mutableListOf<Int>()
        val drag = PanelVolumeDrag({ now }) { _, volume -> sent.add(volume) }
        drag.change("one", -10, 100, true)
        now = 10
        drag.change("one", 200, 100, true)
        assertEquals(listOf(0, 100), sent)
    }
}

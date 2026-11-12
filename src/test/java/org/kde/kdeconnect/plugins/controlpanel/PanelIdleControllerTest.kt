/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import org.junit.Assert.*
import org.junit.Test

class PanelIdleControllerTest {
    private var time = 0L
    private val controller = PanelIdleController { time }
    private fun options(enabled: Boolean = true, timeout: Int = 5000, dial: String = "digital") = PanelIdle(enabled, timeout, dial, "", "")
    private fun start() { controller.configure(options()); controller.setActive(true) }
    private fun idle() { time += 5000; controller.tick(); assertTrue(controller.showing.value) }

    @Test fun defaultIsDisabledAndConfiguredDeadlineIsExact() {
        controller.setActive(true)
        time = 10_000; controller.tick()
        assertFalse(controller.showing.value)
        controller.configure(options())
        time = 14_999; controller.tick(); assertFalse(controller.showing.value)
        time = 15_000; controller.tick(); assertTrue(controller.showing.value)
    }
    @Test fun activePointerPreventsIdleAndReleaseRestartsDeadline() {
        start()
        time = 4900
        assertFalse(controller.touch(PanelIdleController.Touch.START))
        time = 20_000; controller.tick(); assertFalse(controller.showing.value)
        assertFalse(controller.touch(PanelIdleController.Touch.CONTINUE))
        assertFalse(controller.touch(PanelIdleController.Touch.END))
        time += 4999; controller.tick(); assertFalse(controller.showing.value)
        time++; controller.tick(); assertTrue(controller.showing.value)
    }
    @Test fun completeWakingGestureIncludingAdditionalPointersIsConsumed() {
        start(); idle()
        assertTrue(controller.touch(PanelIdleController.Touch.START))
        assertFalse(controller.showing.value)
        repeat(5) { assertTrue(controller.touch(PanelIdleController.Touch.CONTINUE)) }
        assertTrue(controller.touch(PanelIdleController.Touch.END))
        assertFalse(controller.touch(PanelIdleController.Touch.START))
        assertFalse(controller.touch(PanelIdleController.Touch.END))
    }
    @Test fun wakingChordRepeatsAndReleasesNeverReachControls() {
        start(); idle()
        assertTrue(controller.key(59, true))
        assertTrue(controller.key(59, true))
        assertTrue(controller.key(29, true))
        assertTrue(controller.key(59, false))
        assertTrue(controller.key(29, false))
        assertFalse(controller.key(29, true))
        time += 10_000; controller.tick(); assertFalse(controller.showing.value)
        assertFalse(controller.key(29, false))
    }
    @Test fun backgroundDoesNotCountTowardForegroundIdle() {
        start(); idle()
        controller.setActive(false)
        time += 60_000; controller.tick(); assertFalse(controller.showing.value)
        controller.setActive(true)
        time += 4999; controller.tick(); assertFalse(controller.showing.value)
        time++; controller.tick(); assertTrue(controller.showing.value)
    }
    @Test fun duplicateActiveNotificationsDoNotDismissDialOrLoseInputGates() {
        start(); idle()
        controller.setActive(true)
        assertTrue(controller.showing.value)
        assertTrue(controller.touch(PanelIdleController.Touch.START))
        controller.setActive(true)
        assertTrue(controller.touch(PanelIdleController.Touch.END))
        assertFalse(controller.touch(PanelIdleController.Touch.START))
        controller.setActive(true)
        time += 10_000; controller.tick(); assertFalse(controller.showing.value)
        assertFalse(controller.touch(PanelIdleController.Touch.END))
        time += 5000; controller.tick(); assertTrue(controller.showing.value)
    }
    @Test fun focusLossReleasesUnfinishedKeyAndTouchBeforeAFullNewDeadline() {
        start()
        assertFalse(controller.key(29, true))
        assertFalse(controller.touch(PanelIdleController.Touch.START))
        controller.setActive(false)
        time += 60_000; controller.tick(); assertFalse(controller.showing.value)
        controller.setActive(true)
        time += 4999; controller.tick(); assertFalse(controller.showing.value)
        time++; controller.tick(); assertTrue(controller.showing.value)
    }
    @Test fun disablingAndTimeoutChangesDismissButDialChangesDoNot() {
        start(); idle()
        controller.configure(options(dial = "analog"))
        assertTrue(controller.showing.value)
        controller.configure(options(enabled = false))
        assertFalse(controller.showing.value)
        time += 10_000; controller.tick(); assertFalse(controller.showing.value)
        controller.configure(options(timeout = 1000))
        time += 1000; controller.tick(); assertTrue(controller.showing.value)
        controller.configure(null)
        assertFalse(controller.showing.value)
    }
    @Test fun accessibleWakeAlsoRestartsTimerWithoutAnAction() {
        start(); idle()
        assertTrue(controller.wake())
        assertFalse(controller.wake())
        time += 4999; controller.tick(); assertFalse(controller.showing.value)
        time++; controller.tick(); assertTrue(controller.showing.value)
    }
    @Test fun clockHandsIncludeMinuteAndSecondFractions() {
        val angles = PanelClockAngles.at(3, 15, 30)
        assertEquals(97.75f, angles.hour, 0.001f)
        assertEquals(93f, angles.minute, 0.001f)
        assertEquals(180f, angles.second, 0.001f)
        assertEquals(PanelClockAngles.at(0, 0, 0), PanelClockAngles.at(12, 0, 0))
        assertTrue(runCatching { PanelClockAngles.at(24, 0, 0) }.isFailure)
        assertTrue(runCatching { PanelClockAngles.at(0, 60, 0) }.isFailure)
    }
}

/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity-local input gate. A waking gesture never reaches the controls below it. */
class PanelIdleController(private val elapsedMillis: () -> Long) {
    enum class Touch { START, CONTINUE, END }
    private val mutableShowing = MutableStateFlow(false)
    val showing = mutableShowing.asStateFlow()
    private var enabled = false
    private var timeout = 5000L
    private var active = false
    private var lastInput = elapsedMillis()
    private var touching = false
    private var wakingTouch = false
    private val heldKeys = mutableSetOf<Int>()
    private val wakingKeys = mutableSetOf<Int>()

    fun configure(options: PanelIdle?) {
        val nextEnabled = options?.enabled == true
        val nextTimeout = options?.timeoutMs?.toLong() ?: 5000L
        if (enabled != nextEnabled || timeout != nextTimeout) {
            mutableShowing.value = false
            lastInput = elapsedMillis()
        }
        enabled = nextEnabled
        timeout = nextTimeout
    }
    fun setActive(value: Boolean) {
        active = value
        mutableShowing.value = false
        touching = false; wakingTouch = false; heldKeys.clear(); wakingKeys.clear()
        lastInput = elapsedMillis()
    }
    fun tick() {
        if (active && enabled && !touching && heldKeys.isEmpty() && elapsedMillis() - lastInput >= timeout) mutableShowing.value = true
    }
    fun wake(): Boolean {
        val wasShowing = mutableShowing.value
        mutableShowing.value = false
        lastInput = elapsedMillis()
        return wasShowing
    }
    fun touch(phase: Touch): Boolean {
        if (phase == Touch.START) {
            touching = true
            wakingTouch = wake()
        } else lastInput = elapsedMillis()
        val consume = wakingTouch
        if (phase == Touch.END) { touching = false; wakingTouch = false }
        return consume
    }
    fun key(code: Int, down: Boolean): Boolean {
        lastInput = elapsedMillis()
        if (down) {
            heldKeys.add(code)
            val wakingChord = wakingKeys.isNotEmpty()
            if (wake() || wakingChord) wakingKeys.add(code)
            return code in wakingKeys
        }
        heldKeys.remove(code)
        return wakingKeys.remove(code)
    }
}

/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.os.Looper
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf

internal class PanelUiTestComposition : AutoCloseable {
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(Dispatchers.Unconfined + clock)
    val recomposer = Recomposer(scope.coroutineContext)
    private var frameNanos = 0L
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { recomposer.runRecomposeAndApplyChanges() }
    }
    fun await(predicate: () -> Boolean) {
        for (frame in 0 until 50) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            Snapshot.sendApplyNotifications()
            frameNanos += 20_000_000
            clock.sendFrame(frameNanos)
            if (predicate()) return
        }
        assertTrue("Compose did not settle within 50 explicitly driven frames", predicate())
    }
    override fun close() {
        recomposer.cancel()
        scope.cancel()
    }
}

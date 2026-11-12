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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28, 37])
class PanelDisplayCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun fixture() = PanelConfig.parse(File("docs/control-panel/fixtures/config-v1.json").readText())
    private fun device(id: String, paired: Boolean = true) = mockk<Device> {
        every { deviceId } returns id
        every { isPaired } returns paired
    }

    @Test fun cacheUsesExistingPluginScopeAndNeverCrossesDevicesOrPairing() {
        val first = device("panel-display-cache-first")
        val second = device("panel-display-cache-second")
        val owner = ControlPanelPlugin().apply { setContext(context, first) }
        val other = ControlPanelPlugin().apply { setContext(context, second) }
        owner.preferences.edit().clear().putString("panel_config", fixture().encode()).commit()
        other.preferences.edit().clear().commit()
        try {
            val restored = requireNotNull(PanelDisplayCache.offline(context, first))
            assertEquals(fixture(), restored.state.value.config)
            assertEquals(PanelConnection.DISCONNECTED, restored.state.value.connection)
            assertTrue(restored.state.value.pendingButtons.isEmpty())
            assertEquals("", restored.sessionId)
            assertNull(PanelDisplayCache.offline(context, second)?.state?.value?.config)
            assertNull(PanelDisplayCache.offline(context, device("panel-display-cache-first", paired = false)))
        } finally {
            owner.preferences.edit().clear().commit()
            other.preferences.edit().clear().commit()
        }
    }

    @Test fun malformedAndWrongTypedCacheCannotReplaceAnActiveSession() {
        val owner = ControlPanelPlugin().apply { setContext(context, device("panel-display-cache-invalid")) }
        val session = PanelSession().apply { connected(); assertTrue(receiveConfig(fixture().encode())) }
        try {
            owner.preferences.edit().clear().putString("panel_config", "not json").putInt("panel_weather", 7).commit()
            PanelDisplayCache.restore(owner.preferences, session)
            assertEquals(fixture(), session.state.value.config)
            assertEquals(PanelConnection.READY, session.state.value.connection)
            assertNull(session.state.value.weather)
        } finally { owner.preferences.edit().clear().commit() }
    }
}

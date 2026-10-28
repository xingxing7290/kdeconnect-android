/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PanelConfigTest {
    private fun fixture(): PanelConfig = PanelConfig.parse(
        File("docs/control-panel/fixtures/config-v1.json").readText()
    )

    @Test
    fun sharedFixtureRoundTripPreservesFixedSlots() {
        val config = fixture()
        assertEquals(config, PanelConfig.parse(config.encode()))
        assertEquals(18, config.pages.first().slots.size)
        assertNull(config.pages.first().slots[1])
        assertEquals("save", config.pages[1].slots[2]?.id)
        assertEquals("长沙市", config.idle.city)
    }

    @Test
    fun invalidConfigurationsAreRejectedAsAWhole() {
        val config = fixture()
        val firstPage = config.pages.first()
        val invalid = listOf(
            config.copy(schemaVersion = 2),
            config.copy(revision = -1),
            config.copy(revision = PanelConfig.MAX_REVISION + 1),
            config.copy(mode = "other"),
            config.copy(pages = emptyList()),
            config.copy(pages = List(11) { firstPage.copy(id = "page$it") }),
            config.copy(pages = listOf(firstPage, firstPage)),
            config.copy(pages = listOf(firstPage.copy(slots = firstPage.slots.dropLast(1)))),
            config.copy(pages = listOf(firstPage.copy(slots = List(18) { firstPage.slots[0] }))),
            config.copy(activePageId = "missing"),
            config.copy(homePageId = "missing"),
            config.copy(appearance = config.appearance.copy(background = "red")),
            config.copy(appearance = config.appearance.copy(swipeDirection = "diagonal")),
            config.copy(idle = config.idle.copy(timeoutMs = 999)),
            config.copy(idle = config.idle.copy(timeoutMs = 86_400_001)),
            config.copy(idle = config.idle.copy(dialId = "missing")),
            config.copy(idle = config.idle.copy(message = "x".repeat(241))),
        )
        invalid.forEach { value -> assertThrows(IllegalArgumentException::class.java) { value.validated() } }
    }

    @Test
    fun unknownPrivateFieldsAreNotStoredOrReserialized() {
        val raw = fixture().encode().replace("\"kind\":\"website\"", "\"target\":{\"path\":\"private.exe\"},\"kind\":\"website\"")
        val restored = PanelConfig.parse(raw).encode()
        assertFalse(restored.contains("target"))
        assertFalse(restored.contains("private.exe"))
    }

    @Test
    fun malformedTypesAndOversizedUtf8AreRejected() {
        val raw = fixture().encode()
        assertThrows(IllegalArgumentException::class.java) { PanelConfig.parse(raw.replace("\"revision\":7", "\"revision\":7.5")) }
        assertThrows(IllegalArgumentException::class.java) { PanelConfig.parse(raw.replace("\"showLabels\":true", "\"showLabels\":\"true\"")) }
        assertThrows(IllegalArgumentException::class.java) { PanelConfig.parse(" ".repeat(PanelConfig.MAX_BYTES + 1)) }
        assertThrows(IllegalArgumentException::class.java) { PanelConfig.parse("中".repeat(PanelConfig.MAX_BYTES / 2)) }
    }

    @Test
    fun tenPagesAndRevisionBoundaryRemainValid() {
        val config = fixture()
        val pages = List(10) { PanelPage("page$it", "Page $it", slots = List(18) { null }) }
        val maximum = config.copy(revision = PanelConfig.MAX_REVISION, pages = pages, homePageId = "page0", activePageId = "page9")
        assertEquals(maximum, PanelConfig.parse(maximum.encode()))
    }

    @Test
    fun deeplyNestedUnknownFieldsAreRejected() {
        val raw = fixture().encode().dropLast(1) + ",\"ignored\":" + "[".repeat(64) + "0" + "]".repeat(64) + "}"
        assertThrows(IllegalArgumentException::class.java) { PanelConfig.parse(raw) }
        val quoted = fixture().let { it.copy(idle = it.idle.copy(message = "[".repeat(120) + "\\\"")) }
        assertEquals(quoted, PanelConfig.parse(quoted.encode()))
    }
}

/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import org.kde.kdeconnect.helpers.JsonHelper

@Serializable
data class PanelConfig(
    val schemaVersion: Int,
    val revision: Long,
    val mode: String,
    val homePageId: String,
    val activePageId: String,
    val pages: List<PanelPage>,
    val appearance: PanelAppearance,
    val idle: PanelIdle,
) {
    fun validated(): PanelConfig = apply {
        require(schemaVersion == 1) { "unsupported_version" }
        require(revision in 0..MAX_REVISION) { "invalid_revision" }
        require(mode in setOf("single", "pages", "follow")) { "invalid_mode" }
        require(pages.size in 1..MAX_PAGES) { "invalid_page_count" }
        val pageIds = HashSet<String>()
        val buttonIds = HashSet<String>()
        pages.forEach { page ->
            require(ID.matches(page.id) && pageIds.add(page.id)) { "invalid_page_id" }
            require(page.name.isNotBlank() && page.name.length <= 120) { "invalid_page_name" }
            require(page.application.length <= 260 && '\u0000' !in page.application) { "invalid_application" }
            require(page.slots.size == SLOT_COUNT) { "invalid_slot_count" }
            page.slots.filterNotNull().forEach { button ->
                require(ID.matches(button.id) && buttonIds.add(button.id)) { "invalid_button_id" }
                require(button.name.isNotBlank() && button.name.length <= 120) { "invalid_button_name" }
                require(button.kind in BUTTON_KINDS) { "invalid_button_kind" }
                require(button.icon.isEmpty() || ASSET_ID.matches(button.icon)) { "invalid_icon" }
            }
        }
        require(homePageId in pageIds && activePageId in pageIds) { "invalid_active_page" }
        require(COLOR.matches(appearance.background)) { "invalid_background" }
        require(appearance.swipeDirection in setOf("horizontal", "vertical")) { "invalid_swipe_direction" }
        require(idle.timeoutMs in 1_000..86_400_000) { "invalid_idle_timeout" }
        require(idle.dialId in DIALS) { "invalid_dial" }
        require(idle.city.length <= 120 && idle.message.length <= 240) { "invalid_dial_text" }
    }

    fun page(id: String): PanelPage? = pages.find { it.id == id }

    fun button(id: String): PanelButton? = pages.asSequence()
        .flatMap { it.slots.asSequence() }.filterNotNull().find { it.id == id }

    fun encode(): String = JsonHelper.json.encodeToString(validated()).also {
        require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "config_too_large" }
    }

    companion object {
        const val SLOT_COUNT = 18
        const val MAX_PAGES = 10
        const val MAX_BYTES = 256 * 1024
        const val MAX_DEPTH = 64
        const val MAX_REVISION = 9_007_199_254_740_991L
        private val ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        private val ASSET_ID = Regex("[a-f0-9]{64}")
        private val COLOR = Regex("#[a-fA-F0-9]{6}")
        private val BUTTON_KINDS = setOf("program", "website", "file", "folder", "shortcut", "system", "command")
        private val DIALS = setOf("digital", "analog", "weather", "info", "car")

        fun parse(raw: String): PanelConfig {
            require(raw.length <= MAX_BYTES && raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "config_too_large" }
            return JsonHelper.json.decodeFromJsonElement<PanelConfig>(PanelConfigJson.parse(raw)).validated()
        }
    }
}

@Serializable
data class PanelPage(
    val id: String,
    val name: String,
    val application: String = "",
    val slots: List<PanelButton?>,
)

@Serializable
data class PanelButton(val id: String, val kind: String, val name: String, val icon: String = "")

@Serializable
data class PanelAppearance(
    val background: String,
    val showLabels: Boolean,
    val showVolume: Boolean,
    val swipeDirection: String,
)

@Serializable
data class PanelIdle(
    val enabled: Boolean,
    val timeoutMs: Int,
    val dialId: String,
    val city: String,
    val message: String,
)

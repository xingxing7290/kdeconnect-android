/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.kde.kdeconnect.helpers.JsonHelper

/**
 * One Home Assistant entity as displayed by the smart-home dial.
 *
 * The desktop owns the Home Assistant connection; this phone-side model only carries what the
 * dial renders, so no address or token ever reaches the device.
 */
@Serializable
data class PanelHassEntity(
    val id: String,
    val type: String,
    val name: String,
    val state: String,
    val group: String? = null,
    val unit: String? = null,
    val brightness: Int? = null,
    val order: Int = 0,
) {
    val isOn: Boolean get() = state == STATE_ON
    val available: Boolean get() = state.isNotEmpty() && state != STATE_UNAVAILABLE && state != STATE_UNKNOWN
    val controllable: Boolean get() = type == TYPE_SWITCH || type == TYPE_LIGHT

    companion object {
        const val TYPE_SWITCH: String = "switch"
        const val TYPE_LIGHT: String = "light"
        const val TYPE_SENSOR: String = "sensor"
        const val STATE_ON: String = "on"
        const val STATE_UNAVAILABLE: String = "unavailable"
        const val STATE_UNKNOWN: String = "unknown"
        val TYPES: Set<String> = setOf(TYPE_SWITCH, TYPE_LIGHT, TYPE_SENSOR)
    }
}

/**
 * Live snapshot of the smart-home dial, pushed by the desktop whenever an entity changes.
 *
 * This is never persisted: a stale snapshot has no offline value, so the phone keeps the last
 * accepted state in memory and labels it offline instead of inventing readings.
 */
@Serializable
data class PanelHassState(
    val status: String,
    val error: String,
    val fetchedAt: Long,
    val entities: List<PanelHassEntity>,
    val lastCommandEntity: String? = null,
    val lastCommandError: String? = null,
) {
    fun fresh(now: Long, staleMs: Int): Boolean {
        val limit = (staleMs.toLong() / 1000L).coerceAtLeast(1L)
        return status == STATUS_READY && now - fetchedAt in -60..limit
    }

    companion object {
        const val STATUS_READY: String = "ready"
        const val STATUS_CONNECTING: String = "connecting"
        const val STATUS_AUTHENTICATING: String = "authenticating"
        const val STATUS_ERROR: String = "error"
        const val STATUS_DISABLED: String = "disabled"
        val STATUSES: Set<String> = setOf(STATUS_READY, STATUS_CONNECTING, STATUS_AUTHENTICATING, STATUS_ERROR, STATUS_DISABLED)

        const val ACTION_TOGGLE: String = "toggle"
        const val ACTION_TURN_ON: String = "turn_on"
        const val ACTION_TURN_OFF: String = "turn_off"
        val ACTIONS: Set<String> = setOf(ACTION_TOGGLE, ACTION_TURN_ON, ACTION_TURN_OFF)

        const val MAX_ENTITIES: Int = 120

        fun parse(raw: String): PanelHassState? = try {
            require(raw.length <= 65_536 && raw.toByteArray(Charsets.UTF_8).size <= 65_536)
            PanelConfigJson.requireSafeDepth(raw)
            val body = JsonHelper.json.parseToJsonElement(raw) as? JsonObject ?: return null
            fun text(key: String, maximum: Int, allowEmpty: Boolean = false): String? {
                val value = (body[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                return value.takeIf { it.length <= maximum && (allowEmpty || it.isNotBlank()) && it.none(Char::isISOControl) }
            }
            fun integer(key: String) = (body[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

            val status = text("status", 20) ?: return null
            require(status in STATUSES)
            // The error text is cosmetic: an unusable one degrades to "no detail", never drops the packet.
            val error = text("error", 160, true) ?: ""
            val fetched = integer("fetchedAt") ?: return null
            require(fetched in 0..4_102_444_800L)
            val array = body["entities"] as? kotlinx.serialization.json.JsonArray ?: return null
            require(array.size <= MAX_ENTITIES)

            val entities = ArrayList<PanelHassEntity>(array.size)
            val seen = HashSet<String>()
            for (element in array) {
                // A single malformed entity must not discard the whole snapshot: drop the item,
                // keep the rest, so one odd friendly name cannot blank the dial.
                val entity = parseEntity(element) ?: continue
                if (!seen.add(entity.id)) continue
                entities.add(entity)
            }

            // Command feedback is advisory; a malformed block must not invalidate the readings.
            val command = body["lastCommand"] as? JsonObject
            val lastEntity = (command?.get("entityId") as? JsonPrimitive)
                ?.takeIf { it.isString }?.content?.takeIf { it.length <= 128 }
            val lastError = (command?.get("error") as? JsonPrimitive)
                ?.takeIf { it.isString }?.content?.takeIf { it.length <= 64 }
            PanelHassState(status, error, fetched, entities, lastEntity, lastError)
        } catch (_: Exception) {
            null
        }

        private val ENTITY_ID = Regex("^[a-z_]{1,32}\\.[a-z0-9_]{1,96}$")

        /** Returns null for an unusable entry so [parse] can skip it instead of failing whole. */
        private fun parseEntity(element: JsonElement): PanelHassEntity? {
            val item = element as? JsonObject ?: return null
            fun field(key: String, maximum: Int, allowEmpty: Boolean = false): String? {
                val value = (item[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                return value.takeIf { it.length <= maximum && (allowEmpty || it.isNotBlank()) && it.none(Char::isISOControl) }
            }
            fun number(key: String, range: IntRange): Int? = (item[key] as? JsonPrimitive)
                ?.takeUnless { it.isString }?.intOrNull?.takeIf { it in range }

            val id = field("id", 128)?.takeIf { it.matches(ENTITY_ID) } ?: return null
            val type = field("type", 16)?.takeIf { it in PanelHassEntity.TYPES } ?: return null
            val name = field("name", 120, true) ?: return null
            val state = field("state", 64, true) ?: return null
            val group = if (item["group"] == null) null else field("group", 60) ?: return null
            val unit = if (item["unit"] == null) null else field("unit", 16) ?: return null
            val brightness = if (item["brightness"] == null) null else number("brightness", 0..255) ?: return null
            val order = if (item["order"] == null) 0 else number("order", 0..MAX_ENTITIES) ?: return null
            return PanelHassEntity(id, type, name, state, group, unit, brightness, order)
        }
    }
}

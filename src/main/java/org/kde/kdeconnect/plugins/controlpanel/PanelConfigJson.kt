/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import org.kde.kdeconnect.helpers.JsonHelper

/** Enforce wire types before deserialization, which otherwise permits primitive coercion. */
internal object PanelConfigJson {
    fun parse(raw: String): JsonObject {
        requireSafeDepth(raw)
        val root = objectValue(JsonHelper.json.parseToJsonElement(raw))
        root.integer("schemaVersion")
        root.integer("revision")
        root.strings("mode", "homePageId", "activePageId")
        arrayValue(root["pages"]).forEach { value ->
            val page = objectValue(value)
            page.strings("id", "name")
            if ("application" in page) page.strings("application")
            arrayValue(page["slots"]).forEach { slot ->
                if (slot != JsonNull) {
                    val button = objectValue(slot)
                    button.strings("id", "kind", "name")
                    if ("icon" in button) button.strings("icon")
                }
            }
        }
        objectValue(root["appearance"]).apply {
            strings("background", "swipeDirection")
            boolean("showLabels")
            boolean("showVolume")
        }
        objectValue(root["idle"]).apply {
            boolean("enabled")
            integer("timeoutMs")
            strings("dialId", "city", "message")
        }
        return root
    }

    private fun objectValue(value: JsonElement?): JsonObject {
        require(value is JsonObject) { "invalid_config_type" }
        return value
    }

    private fun arrayValue(value: JsonElement?): JsonArray {
        require(value is JsonArray) { "invalid_config_type" }
        return value
    }

    private fun JsonObject.strings(vararg keys: String) {
        keys.forEach { key ->
            val value = get(key)
            require(value is JsonPrimitive && value.isString) { "invalid_config_type" }
        }
    }

    private fun JsonObject.integer(key: String) {
        val value = get(key)
        require(value is JsonPrimitive && !value.isString && value.longOrNull != null) { "invalid_config_type" }
    }

    private fun JsonObject.boolean(key: String) {
        val value = get(key)
        require(value is JsonPrimitive && !value.isString && value.booleanOrNull != null) { "invalid_config_type" }
    }

    // Count containers even in unknown fields; quoted brackets do not contribute.
    private fun requireSafeDepth(raw: String) {
        var depth = 0
        var inString = false
        var escaped = false
        raw.forEach { character ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> inString = true
                    '{', '[' -> require(++depth <= PanelConfig.MAX_DEPTH) { "config_too_deep" }
                    '}', ']' -> depth--
                }
            }
        }
    }
}

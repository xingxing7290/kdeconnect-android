/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.kde.kdeconnect.helpers.JsonHelper

/**
 * WorkBuddy work progress shown in the configurable right column of the information dial.
 *
 * This is a live state packet, never part of panel.json: it carries no configuration revision
 * of its own and is deliberately not persisted across process restarts (a stale progress has
 * no offline value). The phone keeps the last accepted snapshot in memory and labels it stale
 * or offline instead of inventing data.
 */
@Serializable
data class PanelWorkProgress(
    val source: String,
    val status: String,
    val title: String,
    val stage: String,
    val detail: String,
    val percent: Int? = null,
    val sequence: Long,
    val updatedAt: Long,
    val fetchedAt: Long,
) {
    /**
     * Freshness is a transport property: the desktop republishes a heartbeat even when the
     * source content is unchanged. `updatedAt` describes when WorkBuddy produced the data and is
     * only used for the visible "updated N seconds ago" label, so a static source timestamp can
     * never make a healthy live link look stale.
     */
    fun fresh(now: Long, staleMs: Int): Boolean {
        val limit = (staleMs.toLong() / 1000L).coerceAtLeast(1L)
        return status != STATUS_UNAVAILABLE && now - fetchedAt in -60..limit
    }

    fun ageSeconds(now: Long): Long = (now - updatedAt).coerceAtLeast(0)

    fun encode(): String = JsonHelper.json.encodeToString(this)

    companion object {
        const val SOURCE_WORKBUDDY: String = "workbuddy"
        const val STATUS_UNAVAILABLE: String = "unavailable"
        val STATUSES: Set<String> = setOf("running", "idle", "done", "error", "paused", STATUS_UNAVAILABLE)

        fun parse(raw: String): PanelWorkProgress? = try {
            require(raw.length <= 16_384 && raw.toByteArray(Charsets.UTF_8).size <= 16_384)
            PanelConfigJson.requireSafeDepth(raw)
            val body = JsonHelper.json.parseToJsonElement(raw) as? JsonObject ?: return null
            fun text(key: String, maximum: Int, allowEmpty: Boolean = false): String? {
                val value = (body[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                return value.takeIf { it.length <= maximum && (allowEmpty || it.isNotBlank()) && it.none(Char::isISOControl) }
            }
            fun integer(key: String) = (body[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

            val source = text("source", 30) ?: return null
            require(source == SOURCE_WORKBUDDY)
            val status = text("status", 20) ?: return null
            require(status in STATUSES)
            val title = text("title", 120, true) ?: return null
            val stage = text("stage", 120, true) ?: return null
            val detail = text("detail", 240, true) ?: return null
            val percent = if (body["percent"] == null) null
            else (body["percent"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull?.takeIf { it in 0..100 } ?: return null
            val sequence = integer("sequence") ?: return null
            val updated = integer("updatedAt") ?: return null
            val fetched = integer("fetchedAt") ?: return null
            require(sequence in 0..9_007_199_254_740_991L)
            require(updated in 0..4_102_444_800L && fetched in 0..4_102_444_800L && updated <= fetched + 600)
            PanelWorkProgress(source, status, title, stage, detail, percent, sequence, updated, fetched)
        } catch (_: Exception) {
            null
        }
    }
}

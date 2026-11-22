/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.kde.kdeconnect.helpers.JsonHelper

/**
 * Live snapshot of the desktop's hardware counters, pushed by the desktop over KDE Connect.
 *
 * Only aggregated numbers cross the wire: no path, machine name, disk label or process list.
 * This mirrors the desktop private/live split used for weather/progress/hass — nothing here is
 * persisted, and a stale snapshot is shown as offline rather than treated as a fresh reading.
 */
data class PanelSystemInfo(
    val status: String,
    val error: String,
    val fetchedAt: Long,
    val cores: Int?,
    val cpuUsage: Double?,
    val memoryUsage: Double?,
    val memoryUsed: Long?,
    val memoryTotal: Long?,
    val diskUsage: Double?,
    val diskUsed: Long?,
    val diskTotal: Long?,
    val rxBytesPerSec: Long?,
    val txBytesPerSec: Long?,
    val uptimeSeconds: Long?,
    val processCount: Int?,
) {
    fun fresh(now: Long, staleMs: Int): Boolean {
        val limit = (staleMs.toLong() / 1000L).coerceAtLeast(1L)
        return status == STATUS_READY && now - fetchedAt in -60..limit
    }

    val cpuText: String get() = percent(cpuUsage)
    val memoryText: String get() = percent(memoryUsage)
    val diskText: String get() = percent(diskUsage)

    companion object {
        const val STATUS_READY: String = "ready"
        const val STATUS_UNAVAILABLE: String = "unavailable"
        const val STATUS_ERROR: String = "error"
        const val STATUS_DISABLED: String = "disabled"
        val STATUSES: Set<String> = setOf(STATUS_READY, STATUS_UNAVAILABLE, STATUS_ERROR, STATUS_DISABLED)

        private const val MAX_BYTES = 9_000_000_000_000_000_000L

        private fun percent(value: Double?): String =
            if (value == null) "--" else "${Math.round(value * 10) / 10.0}%"

        fun formatBytes(value: Long?): String {
            if (value == null || value < 0) return "--"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var amount = value.toDouble()
            var index = 0
            while (amount >= 1024 && index < units.size - 1) { amount /= 1024; index++ }
            val formatted = if (amount >= 10) Math.round(amount).toString() else "${Math.round(amount * 10) / 10.0}"
            return "$formatted ${units[index]}"
        }

        fun formatRate(value: Long?): String = if (value == null) "--" else "${formatBytes(value)}/s"

        fun formatUptime(seconds: Long?): String {
            if (seconds == null || seconds < 0) return "--"
            val days = seconds / 86400
            val hours = (seconds % 86400) / 3600
            val minutes = (seconds % 3600) / 60
            return (if (days > 0) "${days}d " else "") + "${hours}h ${minutes}m"
        }

        fun parse(raw: String): PanelSystemInfo? = try {
            require(raw.length <= 16_384 && raw.toByteArray(Charsets.UTF_8).size <= 16_384)
            PanelConfigJson.requireSafeDepth(raw)
            val body = JsonHelper.json.parseToJsonElement(raw) as? JsonObject ?: return null

            fun text(obj: JsonObject, key: String, maximum: Int): String? {
                val value = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                return value.takeIf { it.length <= maximum && it.none(Char::isISOControl) }
            }
            fun number(obj: JsonObject, key: String): Double? = (obj[key] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
            fun bounded(obj: JsonObject, key: String, min: Double, max: Double): Double? =
                number(obj, key)?.takeIf { it >= min && it <= max && !it.isNaN() }
            fun bytes(obj: JsonObject, key: String): Long? =
                number(obj, key)?.takeIf { it >= 0 && it <= MAX_BYTES && !it.isNaN() }?.toLong()

            val status = text(body, "status", 20) ?: return null
            require(status in STATUSES)
            val error = text(body, "error", 160).takeIf { !it.isNullOrBlank() } ?: ""

            val fetched = (body["fetchedAt"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: return null
            require(fetched in 0..4_102_444_800L)

            val cores = bounded(body, "cores", 1.0, 1024.0)?.toInt()
            val cpu = body["cpu"] as? JsonObject
            val cpuUsage = cpu?.let { bounded(it, "usage", 0.0, 100.0) }

            val memory = body["memory"] as? JsonObject
            val memoryUsage = memory?.let { bounded(it, "usage", 0.0, 100.0) }
            val memoryUsed = memory?.let { bytes(it, "usedBytes") }
            val memoryTotal = memory?.let { bytes(it, "totalBytes") }

            val disk = body["disk"] as? JsonObject
            val diskUsage = disk?.let { bounded(it, "usage", 0.0, 100.0) }
            val diskUsed = disk?.let { bytes(it, "usedBytes") }
            val diskTotal = disk?.let { bytes(it, "totalBytes") }

            val network = body["network"] as? JsonObject
            val rx = network?.let { bytes(it, "rxBytesPerSec") }
            val tx = network?.let { bytes(it, "txBytesPerSec") }

            val uptime = bounded(body, "uptimeSeconds", 0.0, 4_102_444_800.0)?.toLong()
            val processes = bounded(body, "processCount", 0.0, 100_000.0)?.toInt()

            PanelSystemInfo(
                status, error, fetched, cores, cpuUsage,
                memoryUsage, memoryUsed, memoryTotal,
                diskUsage, diskUsed, diskTotal,
                rx, tx, uptime, processes,
            )
        } catch (_: Exception) {
            null
        }
    }
}

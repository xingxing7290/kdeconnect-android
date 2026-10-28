/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

/** Keeps one gesture on its original output and limits traffic while dragging. */
class PanelVolumeDrag(
    private val elapsedMillis: () -> Long,
    private val send: (String, Int) -> Unit,
) {
    private var target: String? = null
    private var value: Int? = null
    private var lastSentAt: Long? = null

    fun change(output: String?, requested: Int, maximum: Int, enabled: Boolean): Int? {
        if (!enabled || output == null || maximum <= 0) return null
        if (target == null) target = output
        if (target != output) return null
        value = requested.coerceIn(0, maximum)
        val now = elapsedMillis()
        if (lastSentAt == null || now < checkNotNull(lastSentAt) || now - checkNotNull(lastSentAt) >= 80) {
            send(output, checkNotNull(value))
            lastSentAt = now
        }
        return value
    }

    fun finish(output: String?, enabled: Boolean) {
        if (enabled && target != null && target == output) value?.let { send(checkNotNull(target), it) }
        target = null
        value = null
        lastSentAt = null
    }
}

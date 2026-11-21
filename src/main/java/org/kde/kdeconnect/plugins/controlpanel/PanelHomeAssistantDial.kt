/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import org.kde.kdeconnect_tp.R

/**
 * Smart-home dial.
 *
 * The idle dial is display-only: [PanelIdleController.touch] swallows the waking gesture, so a
 * tap on the dial never reaches Compose. Everything that needs a tap therefore lives in
 * [PanelHassSheet], which is opened from the control panel. This surface only reports state so
 * the dial stays a glanceable summary.
 *
 * The clock is deliberately small and pinned to the top-right corner because the entity list is
 * the primary content.
 */
@Composable
internal fun PanelHomeAssistantDial(
    now: Calendar,
    hass: PanelHassState?,
    modifier: Modifier,
    ink: Color = Color(0xFF202722),
    quiet: Color = Color(0xFF5F655F),
    connection: PanelConnection = PanelConnection.READY,
    nowSeconds: Long = now.timeInMillis / 1000,
) {
    val fresh = hass?.fresh(nowSeconds, HASS_STALE_MS) == true
    val entities = hass?.entities.orEmpty()
    val clock = panelHassClock(now)

    Column(modifier.padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelDialText(
                text = stringResource(R.string.control_panel_dial_hass_title),
                color = ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PanelDialText(
                text = clock,
                color = quiet,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.testTag("panel-dial-hass-clock"),
            )
        }
        Spacer(Modifier.height(6.dp))

        val notice = when {
            connection != PanelConnection.READY -> stringResource(R.string.control_panel_dial_hass_offline)
            hass == null -> stringResource(R.string.control_panel_dial_hass_not_configured)
            hass.status == PanelHassState.STATUS_DISABLED -> stringResource(R.string.control_panel_dial_hass_not_configured)
            hass.status == PanelHassState.STATUS_ERROR -> stringResource(R.string.control_panel_dial_hass_offline)
            hass.status != PanelHassState.STATUS_READY -> stringResource(R.string.control_panel_dial_hass_connecting)
            !fresh -> stringResource(R.string.control_panel_dial_hass_stale)
            entities.isEmpty() -> stringResource(R.string.control_panel_dial_hass_empty)
            else -> null
        }
        if (notice != null) {
            PanelDialText(
                text = notice,
                color = quiet,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("panel-dial-hass-notice"),
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            panelHassGroups(entities).forEach { (group, items) ->
                if (group.isNotEmpty()) {
                    PanelDialText(
                        text = group,
                        color = quiet,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, bottom = 1.dp),
                    )
                }
                items.forEachIndexed { index, entity ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(ink.copy(alpha = 0.10f)),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("panel-dial-hass-${entity.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PanelDialText(
                            text = entity.name.ifBlank { entity.id },
                            color = if (entity.available) ink else quiet,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        PanelDialText(
                            text = panelHassSummary(entity),
                            color = if (entity.isOn) ink else quiet,
                            fontSize = 12.sp,
                            fontWeight = if (entity.isOn) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Stable group order: first appearance wins, so the desktop sort order is preserved. */
private fun panelHassGroups(entities: List<PanelHassEntity>): List<Pair<String, List<PanelHassEntity>>> {
    val order = LinkedHashMap<String, MutableList<PanelHassEntity>>()
    entities.forEach { entity ->
        val group = entity.group.orEmpty()
        order.getOrPut(group) { ArrayList() }.add(entity)
    }
    return order.map { (group, items) -> group to items.toList() }
}

@Composable
private fun panelHassSummary(entity: PanelHassEntity): String {
    if (!entity.available) {
        return if (entity.state.isBlank()) {
            stringResource(R.string.control_panel_dial_hass_unknown)
        } else {
            stringResource(R.string.control_panel_dial_hass_unavailable)
        }
    }
    if (entity.type == PanelHassEntity.TYPE_SENSOR) {
        val unit = entity.unit
        return if (unit.isNullOrBlank()) entity.state else "${entity.state} $unit"
    }
    val on = stringResource(if (entity.isOn) R.string.control_panel_dial_hass_on else R.string.control_panel_dial_hass_off)
    val brightness = entity.brightness
    return if (entity.type == PanelHassEntity.TYPE_LIGHT && entity.isOn && brightness != null) {
        "$on ${(brightness * 100) / 255}%"
    } else {
        on
    }
}

private fun panelHassClock(now: Calendar): String {
    return SimpleDateFormat("HH:mm", panelDisplayLocale()).format(Date(now.timeInMillis))
}

/**
 * Freshness window. Home Assistant is push-based, so the desktop only refreshes `fetchedAt` on a
 * change or on its 30 s keep-alive poll; a shorter window would label a quiet house as stale.
 */
internal const val HASS_STALE_MS: Int = 45_000

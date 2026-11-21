/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * The entity cards are the primary content; the clock is deliberately small and pinned to the
 * top-right corner. Readings come from the desktop, which is the only node that talks to Home
 * Assistant, so the phone never needs network access to the household server.
 */
@Composable
internal fun PanelHomeAssistantDial(
    options: PanelIdle,
    now: Calendar,
    hass: PanelHassState?,
    modifier: Modifier,
    ink: Color = Color(0xFF202722),
    quiet: Color = Color(0xFF5F655F),
    connection: PanelConnection = PanelConnection.READY,
    nowSeconds: Long = now.timeInMillis / 1000,
    onCommand: (entityId: String, action: String, brightness: Int) -> Unit = { _, _, _ -> },
) {
    val fresh = hass?.fresh(nowSeconds, 15000) == true
    val entities = hass?.entities.orEmpty()
    val clock = panelHassClock(now)

    BoxWithConstraints(
        modifier
            .border(3.dp, ink, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                entities.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { entity ->
                            PanelHassCard(
                                entity = entity,
                                ink = ink,
                                quiet = quiet,
                                onCommand = onCommand,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHassCard(
    entity: PanelHassEntity,
    ink: Color,
    quiet: Color,
    onCommand: (String, String, Int) -> Unit,
    modifier: Modifier,
) {
    val on = entity.isOn
    val enabled = entity.controllable && entity.available
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) ink.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, ink.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) {
                onCommand(entity.id, PanelHassState.ACTION_TOGGLE, -1)
            }
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .testTag("panel-dial-hass-${entity.id}"),
    ) {
        PanelDialText(
            text = entity.name.ifBlank { entity.id },
            color = if (entity.available) ink else quiet,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        when (entity.type) {
            PanelHassEntity.TYPE_SENSOR -> PanelDialText(
                text = panelHassReading(entity),
                color = ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            else -> PanelDialText(
                text = stringResource(if (on) R.string.control_panel_dial_hass_on else R.string.control_panel_dial_hass_off),
                color = if (on) ink else quiet,
                fontSize = 15.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
        if (entity.type == PanelHassEntity.TYPE_LIGHT && on && entity.brightness != null) {
            PanelDialText(
                text = "${((entity.brightness ?: 0) * 100) / 255}%",
                color = quiet,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

private fun panelHassReading(entity: PanelHassEntity): String {
    val unit = entity.unit
    return if (unit.isNullOrBlank()) entity.state else "${entity.state} $unit"
}

private fun panelHassClock(now: Calendar): String {
    return SimpleDateFormat("HH:mm", panelDisplayLocale()).format(Date(now.timeInMillis))
}

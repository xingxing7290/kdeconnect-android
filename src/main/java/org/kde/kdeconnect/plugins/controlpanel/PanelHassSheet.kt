/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect_tp.R

/**
 * Interactive smart-home surface.
 *
 * The idle dial cannot take input ([PanelIdleController.touch] consumes the waking gesture), so
 * this sheet — opened from the control panel — is where entities are actually switched. It uses
 * the same snapshot the dial renders, and every tap is sent through
 * [ControlPanelPlugin.sendHassCommand].
 */
@Composable
internal fun PanelHassSheet(
    hass: PanelHassState?,
    connection: PanelConnection,
    onCommand: (entityId: String, action: String, brightness: Int) -> Unit,
    onRefresh: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    nowSeconds: Long = System.currentTimeMillis() / 1000,
) {
    val fresh = hass?.fresh(nowSeconds, HASS_STALE_MS) == true
    val entities = hass?.entities.orEmpty()
    val live = connection == PanelConnection.READY && hass?.status == PanelHassState.STATUS_READY && fresh

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.control_panel_hass_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh, modifier = Modifier.testTag("panel-hass-refresh")) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.control_panel_hass_refresh))
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("panel-hass-close")) {
                    Icon(Icons.Default.Close, stringResource(R.string.control_panel_hass_close))
                }
            }
            Text(
                panelHassStatusText(hass, connection, fresh),
                modifier = Modifier.fillMaxWidth().testTag("panel-hass-status"),
                fontSize = 12.sp,
                color = if (live) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (entities.isEmpty()) {
                Text(
                    panelHassEmptyText(hass),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("panel-hass-empty"),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val groups = LinkedHashMap<String, MutableList<PanelHassEntity>>()
            entities.forEach { entity -> groups.getOrPut(entity.group.orEmpty()) { ArrayList() }.add(entity) }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).testTag("panel-hass-list"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                groups.forEach { (group, members) ->
                    if (group.isNotEmpty()) {
                        item(key = "group-$group") {
                            Text(
                                group,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    items(members, key = { it.id }) { entity ->
                        PanelHassRow(entity, enabled = live, onCommand = onCommand)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHassRow(
    entity: PanelHassEntity,
    enabled: Boolean,
    onCommand: (String, String, Int) -> Unit,
) {
    val canAct = enabled && entity.controllable && entity.available
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("panel-hass-row-${entity.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entity.name.ifBlank { entity.id },
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (entity.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                panelHassValueText(entity),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (entity.controllable) {
            if (entity.type == PanelHassEntity.TYPE_LIGHT && entity.isOn && entity.available) {
                val brightness = entity.brightness ?: 128
                TextButton(
                    onClick = { onCommand(entity.id, PanelHassState.ACTION_TURN_ON, (brightness - 26).coerceAtLeast(0)) },
                    enabled = canAct,
                ) {
                    Text("－", fontSize = 16.sp)
                }
                Text("${(brightness * 100) / 255}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = { onCommand(entity.id, PanelHassState.ACTION_TURN_ON, (brightness + 26).coerceAtMost(255)) },
                    enabled = canAct,
                ) {
                    Text("＋", fontSize = 16.sp)
                }
            }
            Switch(
                checked = entity.isOn,
                onCheckedChange = { on ->
                    onCommand(
                        entity.id,
                        if (on) PanelHassState.ACTION_TURN_ON else PanelHassState.ACTION_TURN_OFF,
                        if (entity.type == PanelHassEntity.TYPE_LIGHT && on) entity.brightness ?: -1 else -1,
                    )
                },
                enabled = canAct,
                modifier = Modifier.testTag("panel-hass-switch-${entity.id}"),
            )
        }
    }
}

@Composable
private fun panelHassValueText(entity: PanelHassEntity): String {
    if (!entity.available) return stringResource(R.string.control_panel_dial_hass_unavailable)
    if (entity.type == PanelHassEntity.TYPE_SENSOR) {
        val unit = entity.unit
        return if (unit.isNullOrBlank()) entity.state else "${entity.state} $unit"
    }
    return stringResource(if (entity.isOn) R.string.control_panel_dial_hass_on else R.string.control_panel_dial_hass_off)
}

@Composable
private fun panelHassStatusText(hass: PanelHassState?, connection: PanelConnection, fresh: Boolean): String {
    return when {
        connection != PanelConnection.READY -> stringResource(R.string.control_panel_dial_hass_offline)
        hass == null -> stringResource(R.string.control_panel_dial_hass_not_configured)
        hass.status == PanelHassState.STATUS_DISABLED -> stringResource(R.string.control_panel_dial_hass_not_configured)
        hass.status == PanelHassState.STATUS_ERROR -> stringResource(R.string.control_panel_hass_error, hass.error)
        hass.status == PanelHassState.STATUS_CONNECTING || hass.status == PanelHassState.STATUS_AUTHENTICATING ->
            stringResource(R.string.control_panel_dial_hass_connecting)
        !fresh -> stringResource(R.string.control_panel_dial_hass_stale)
        else -> stringResource(R.string.control_panel_hass_ready)
    }
}

@Composable
private fun panelHassEmptyText(hass: PanelHassState?): String {
    return when {
        hass == null || hass.status == PanelHassState.STATUS_DISABLED -> stringResource(R.string.control_panel_dial_hass_not_configured)
        hass.status == PanelHassState.STATUS_ERROR -> stringResource(R.string.control_panel_dial_hass_offline)
        else -> stringResource(R.string.control_panel_dial_hass_empty)
    }
}

/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * Desktop hardware dial: a glanceable, read-only summary of the paired computer's CPU, memory,
 * disk, network and uptime. Display-only by design — the idle controller swallows the waking
 * gesture, so nothing here is tappable. The clock is small and pinned to a corner because the
 * hardware readings are the primary content.
 */
@Composable
internal fun PanelSystemDial(
    now: Calendar,
    sysInfo: PanelSystemInfo?,
    modifier: Modifier,
    ink: Color = Color(0xFF202722),
    quiet: Color = Color(0xFF5F655F),
    connection: PanelConnection = PanelConnection.READY,
    nowSeconds: Long = now.timeInMillis / 1000,
) {
    val fresh = sysInfo?.fresh(nowSeconds, SYSINFO_STALE_MS) == true
    val clock = SimpleDateFormat("HH:mm", panelDisplayLocale()).format(Date(now.timeInMillis))

    Column(modifier.padding(10.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelDialText(
                text = stringResource(R.string.control_panel_dial_sys_title),
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
                modifier = Modifier.testTag("panel-dial-sys-clock"),
            )
        }
        Spacer(Modifier.height(6.dp))

        val notice = sysInfoNotice(connection, sysInfo, fresh)
        if (notice != null) {
            PanelDialText(
                text = notice,
                color = quiet,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("panel-dial-sys-notice"),
            )
            return@Column
        }

        val state = checkNotNull(sysInfo)
        SysRow(stringResource(R.string.control_panel_dial_sys_cpu), state.cpuText, ink, quiet)
        SysRow(stringResource(R.string.control_panel_dial_sys_memory), state.memoryText, ink, quiet)
        SysRow(stringResource(R.string.control_panel_dial_sys_disk), state.diskText, ink, quiet)
        SysRow(stringResource(R.string.control_panel_dial_sys_network),
            "${PanelSystemInfo.formatRate(state.rxBytesPerSec)} / ${PanelSystemInfo.formatRate(state.txBytesPerSec)}", ink, quiet)
        SysRow(stringResource(R.string.control_panel_dial_sys_uptime), PanelSystemInfo.formatUptime(state.uptimeSeconds), ink, quiet)
        SysRow(stringResource(R.string.control_panel_dial_sys_processes), state.processCount?.toString() ?: "--", ink, quiet)
    }
}

@Composable
private fun SysRow(label: String, value: String, ink: Color, quiet: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PanelDialText(text = label, color = quiet, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
        PanelDialText(
            text = value,
            color = ink,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun sysInfoNotice(connection: PanelConnection, sysInfo: PanelSystemInfo?, fresh: Boolean): String? {
    return when {
        connection != PanelConnection.READY -> stringResource(R.string.control_panel_dial_sys_offline)
        sysInfo == null || sysInfo.status == PanelSystemInfo.STATUS_DISABLED ->
            stringResource(R.string.control_panel_dial_sys_not_configured)
        sysInfo.status == PanelSystemInfo.STATUS_ERROR -> stringResource(R.string.control_panel_dial_sys_error)
        !fresh -> stringResource(R.string.control_panel_dial_sys_stale)
        else -> null
    }
}

/** Shares the stale window with the dial so it flips to "out of date" predictably. */
internal const val SYSINFO_STALE_MS: Int = 20_000

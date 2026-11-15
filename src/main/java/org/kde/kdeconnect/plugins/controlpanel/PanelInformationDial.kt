/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import org.kde.kdeconnect_tp.R
import kotlin.math.roundToInt

/**
 * Information dial.
 *
 * LEFT column (battery, clock, current weather) is a fixed device/environment area: its layout,
 * content, weights and test tags are intentionally untouched. RIGHT column is the configurable
 * information area: user notes, optional WorkBuddy work progress and optional three-day forecast.
 * Footer rows span the full width and also stay unchanged. See docs/control-panel/INFO.zh-CN.md.
 */
@Composable
internal fun PanelInformationDial(
    options: PanelIdle,
    now: Calendar,
    weather: PanelWeatherSnapshot?,
    modifier: Modifier,
    ink: Color = Color(0xFF202722),
    quiet: Color = Color(0xFF5F655F),
    progress: PanelWorkProgress? = null,
    connection: PanelConnection = PanelConnection.READY,
    nowSeconds: Long = now.timeInMillis / 1000,
) {
    val battery = rememberPanelBatteryState()
    val seconds = now.timeInMillis / 1000
    val fresh = weather?.fresh(seconds) == true && weather.status !in setOf("disabled", "no_city")
    val days = weather?.forecastDays(seconds).orEmpty()
    val locale = panelDisplayLocale()
    val info = options.info
    BoxWithConstraints(modifier.border(3.dp, ink, RoundedCornerShape(14.dp)).padding(10.dp)) {
        if (maxHeight.value / LocalDensity.current.fontScale < 200f) {
            PanelCompactInformationDial(options, now, weather, battery, ink, quiet, progress, connection, nowSeconds)
            return@BoxWithConstraints
        }
        val unit = minOf(maxWidth.value / 720f, maxHeight.value / 360f).coerceIn(0.45f, 2f)
        val gap = (8 * unit).dp
        val messageLines = if (maxHeight < 320.dp) 2 else 3
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                // ---- LEFT: fixed device/environment area. Do not restructure. ----
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(Modifier.fillMaxWidth().weight(0.17f).border(1.dp, ink).padding(horizontal = gap, vertical = gap / 2), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        PanelDialText(stringResource(R.string.control_panel_local_battery), color = ink, fontSize = (17 * unit).sp, maxLines = 1)
                        PanelDialText((battery.percent?.let { "$it%" } ?: "--%") + if (battery.charging) " +" else "", color = ink, fontSize = (23 * unit).sp,
                            fontFamily = FontFamily.Monospace, maxLines = 1, modifier = Modifier.testTag("panel-local-battery"))
                    }
                    Row(Modifier.fillMaxWidth().weight(0.36f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(gap)) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            val clockSize = minOf(81 * unit, maxHeight.value / 1.5f).sp
                            PanelDialText(SimpleDateFormat("HH:mm", locale).format(now.time), color = ink, fontSize = clockSize,
                                fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace, maxLines = 1, modifier = Modifier.align(Alignment.CenterStart))
                        }
                        PanelDialText(SimpleDateFormat("ss", locale).format(now.time), color = quiet, fontSize = (30 * unit).sp, modifier = Modifier.padding(bottom = gap))
                    }
                    Column(Modifier.fillMaxWidth().weight(0.47f).border(1.dp, ink).padding(gap / 2).testTag("panel-info-current-weather")) {
                        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            PanelWeatherGlyph(if (fresh) weather?.weatherCode else null, ink, Modifier.weight(0.45f).fillMaxHeight())
                            PanelDialText(if (fresh) "${checkNotNull(weather?.temperature).roundToInt()}°C" else "--°C", color = ink,
                                fontSize = (37 * unit).sp, lineHeight = (42 * unit).sp, maxLines = 1,
                                modifier = Modifier.weight(0.55f).testTag("panel-weather-temperature"))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                            PanelDialText(panelWeatherStatus(weather, seconds), color = ink, fontSize = (16 * unit).sp, lineHeight = (20 * unit).sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("panel-weather-status"))
                            val today = days.firstOrNull()?.takeIf { it.date == PanelWeatherSnapshot.dateAtOffset(seconds, weather?.utcOffsetSeconds ?: 0) }
                            PanelDialText(today?.let { "${it.minimum.roundToInt()}° / ${it.maximum.roundToInt()}°" } ?: "--° / --°", color = quiet,
                                fontSize = (16 * unit).sp, lineHeight = (20 * unit).sp, maxLines = 1,
                                modifier = Modifier.testTag("panel-info-today-range"))
                        }
                    }
                }
                // ---- RIGHT: configurable information area. ----
                Column(Modifier.weight(1.05f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    // The default combination reproduces the historical 0.28 / 0.72 split exactly.
                    val noteWeight = when {
                        info.showProgress && info.showForecast -> 0.22f
                        info.showProgress -> 0.34f
                        info.showForecast -> 0.28f
                        else -> 1f
                    }
                    val progressWeight = if (info.showProgress) if (info.showForecast) 0.28f else 0.66f else 0f
                    val forecastWeight = if (info.showForecast) if (info.showProgress) 0.50f else 0.72f else 0f
                    Box(Modifier.fillMaxWidth().weight(noteWeight).border(1.dp, ink).padding(gap), contentAlignment = Alignment.CenterStart) {
                        PanelInfoNotes(info.notes, options.message, ink, quiet, (18 * unit).sp, messageLines, gap)
                    }
                    if (info.showProgress) {
                        Box(Modifier.fillMaxWidth().weight(progressWeight).border(1.dp, ink).padding(gap)) {
                            PanelWorkProgressView(progress, info.staleMs, nowSeconds, connection, ink, quiet, unit, gap)
                        }
                    }
                    if (info.showForecast) {
                        Column(Modifier.fillMaxWidth().weight(forecastWeight).border(1.dp, ink).padding(gap), verticalArrangement = Arrangement.SpaceEvenly) {
                            repeat(3) { index ->
                                val day = days.getOrNull(index)
                                Row(Modifier.fillMaxWidth().weight(1f).testTag("panel-forecast-$index"), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(gap)) {
                                    Column(Modifier.weight(1f)) {
                                        PanelDialText(day?.label(locale) ?: stringResource(R.string.control_panel_forecast_unavailable), color = ink,
                                            fontSize = (18 * unit).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        PanelDialText(day?.let { stringResource(weatherDescription(it.weatherCode)) } ?: "--", color = quiet,
                                            fontSize = (12 * unit).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    PanelWeatherGlyph(day?.weatherCode, ink, Modifier.width((38 * unit).dp).fillMaxHeight(0.65f))
                                    PanelDialText(day?.let { "${it.minimum.roundToInt()}° / ${it.maximum.roundToInt()}°" } ?: "--° / --°", color = ink,
                                        fontSize = (21 * unit).sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().border(1.dp, ink).padding(gap), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap)) {
                PanelDialText(SimpleDateFormat("EEE  MM/dd", locale).format(now.time), color = ink, fontSize = (19 * unit).sp,
                    maxLines = 1, modifier = Modifier.weight(0.8f), overflow = TextOverflow.Ellipsis)
                PanelDialText(weather?.resolvedCity ?: options.city.ifBlank { stringResource(R.string.control_panel_dial_no_city) }, color = ink,
                    fontSize = (17 * unit).sp, maxLines = 1, modifier = Modifier.weight(1.2f), overflow = TextOverflow.Ellipsis)
            }
            Column {
                PanelDialText(stringResource(R.string.control_panel_weather_source) + (weather?.timezone?.let { " · $it" } ?: ""), color = quiet,
                    fontSize = (10 * unit).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (weather?.hasObservation == true) {
                    val formatter = SimpleDateFormat("MM-dd HH:mm", locale)
                    PanelDialText(stringResource(R.string.control_panel_weather_updated, formatter.format(Date(checkNotNull(weather.observedAt) * 1000)),
                        formatter.format(Date(checkNotNull(weather.fetchedAt) * 1000))), color = quiet, fontSize = (10 * unit).sp, maxLines = 1)
                }
            }
        }
    }
}

/** User notes; an empty list falls back to the legacy single message, preserving old configurations. */
@Composable
private fun PanelInfoNotes(notes: List<String>, message: String, ink: Color, quiet: Color, size: TextUnit,
    lines: Int, gap: Dp) {
    val entries = notes.filter { it.isNotBlank() }.ifEmpty { listOf(message).filter { it.isNotBlank() } }
    if (entries.isEmpty()) {
        PanelDialText(stringResource(R.string.control_panel_info_notes_hint), color = quiet, fontSize = size, maxLines = lines, overflow = TextOverflow.Ellipsis)
        return
    }
    val perNote = if (entries.size > 1) maxOf(1, lines / entries.size) else lines
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap / 3), verticalAlignment = Alignment.CenterVertically) {
        entries.take(4).forEach { note ->
            PanelDialText(if (entries.size > 1) "· $note" else note, color = ink, fontSize = size, lineHeight = size * 1.2f,
                maxLines = perNote, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Live WorkBuddy progress: shows the last known values with an explicit fresh/offline state. */
@Composable
private fun PanelWorkProgressView(progress: PanelWorkProgress?, staleMs: Int, nowSeconds: Long, connection: PanelConnection,
    ink: Color, quiet: Color, unit: Float, gap: Dp) {
    val usable = progress != null && progress.status != PanelWorkProgress.STATUS_UNAVAILABLE
    val fresh = usable && connection == PanelConnection.READY && checkNotNull(progress).fresh(nowSeconds, staleMs)
    val stateLabel = when {
        connection != PanelConnection.READY -> stringResource(R.string.control_panel_progress_offline)
        !usable -> stringResource(R.string.control_panel_progress_waiting)
        !fresh -> stringResource(R.string.control_panel_progress_stale)
        else -> progressStatusLabel(checkNotNull(progress).status)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap / 3)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PanelDialText(stringResource(R.string.control_panel_progress_title), Modifier.weight(1f), color = ink,
                fontSize = (14 * unit).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PanelDialText(stateLabel, color = if (fresh) ink else quiet, fontSize = (10 * unit).sp, maxLines = 1,
                modifier = Modifier.testTag("panel-progress-state"))
        }
        if (!usable) {
            PanelDialText(stringResource(R.string.control_panel_progress_waiting), color = quiet,
                fontSize = (12 * unit).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            return@Column
        }
        val value = checkNotNull(progress)
        if (value.title.isNotBlank()) PanelDialText(value.title, Modifier.fillMaxWidth().testTag("panel-progress-title"), color = ink,
            fontSize = (15 * unit).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap / 2), verticalAlignment = Alignment.CenterVertically) {
            PanelDialText(value.stage.ifBlank { stateLabel }, Modifier.weight(1f), color = ink, fontSize = (13 * unit).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            value.percent?.let { percent ->
                PanelDialText(stringResource(R.string.control_panel_progress_percent, percent), color = ink,
                    fontSize = (14 * unit).sp, fontFamily = FontFamily.Monospace, maxLines = 1,
                    modifier = Modifier.testTag("panel-progress-percent"))
            }
        }
        value.percent?.let { percent ->
            val height = (6f * unit).coerceAtLeast(3f).dp
            Box(Modifier.fillMaxWidth().height(height).background(quiet.copy(alpha = 0.18f), RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().background(ink, RoundedCornerShape(50)))
            }
        }
        if (value.detail.isNotBlank()) PanelDialText(value.detail, Modifier.fillMaxWidth(), color = quiet,
            fontSize = (11 * unit).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val age = value.ageSeconds(nowSeconds)
        PanelDialText(
            if (age < 60) stringResource(R.string.control_panel_progress_updated_seconds, age.toInt())
            else stringResource(R.string.control_panel_progress_updated_minutes, (age / 60).toInt()),
            color = quiet, fontSize = (10 * unit).sp, maxLines = 1, modifier = Modifier.testTag("panel-progress-updated"))
    }
}

@Composable
internal fun progressStatusLabel(status: String): String = stringResource(when (status) {
    "running" -> R.string.control_panel_progress_running
    "idle" -> R.string.control_panel_progress_idle
    "done" -> R.string.control_panel_progress_done
    "error" -> R.string.control_panel_progress_error
    "paused" -> R.string.control_panel_progress_paused
    else -> R.string.control_panel_progress_unavailable
})

@Composable
private fun PanelCompactInformationDial(options: PanelIdle, now: Calendar, weather: PanelWeatherSnapshot?, battery: PanelBatteryState,
    ink: Color, quiet: Color = Color(0xFF5F655F), progress: PanelWorkProgress? = null,
    connection: PanelConnection = PanelConnection.READY, nowSeconds: Long = now.timeInMillis / 1000) {
    val locale = panelDisplayLocale()
    val seconds = now.timeInMillis / 1000
    val fresh = weather?.fresh(seconds) == true && weather.status !in setOf("disabled", "no_city")
    val days = weather?.forecastDays(seconds).orEmpty()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelDialText(SimpleDateFormat("HH:mm:ss", locale).format(now.time), Modifier.weight(1f), color = ink, fontSize = 28.sp, lineHeight = 32.sp,
                fontFamily = FontFamily.Monospace, maxLines = 1)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PanelDialText(stringResource(R.string.control_panel_local_battery), color = ink, fontSize = 12.sp, maxLines = 1)
                PanelDialText((battery.percent?.let { "$it%" } ?: "--%") + if (battery.charging) " +" else "", color = ink, fontSize = 14.sp,
                    maxLines = 1, modifier = Modifier.testTag("panel-local-battery"))
            }
        }
        val note = options.info.notes.firstOrNull { it.isNotBlank() } ?: options.message
        PanelDialText(note.ifBlank { stringResource(R.string.control_panel_info_notes_hint) },
            color = ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (options.info.showProgress && progress != null && progress.status != PanelWorkProgress.STATUS_UNAVAILABLE) {
            val freshProgress = connection == PanelConnection.READY && progress.fresh(nowSeconds, options.info.staleMs)
            PanelDialText(
                stringResource(R.string.control_panel_progress_title) + " · " +
                    (progress.stage.ifBlank { progressStatusLabel(progress.status) }) +
                    (progress.percent?.let { " " + stringResource(R.string.control_panel_progress_percent, it) } ?: "") +
                    (if (freshProgress) "" else " · " + stringResource(R.string.control_panel_progress_stale)),
                Modifier.fillMaxWidth().testTag("panel-progress-compact"), color = if (freshProgress) ink else quiet,
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().border(1.dp, ink).padding(3.dp).testTag("panel-info-current-weather"),
                verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    PanelWeatherGlyph(if (fresh) weather?.weatherCode else null, ink, Modifier.size(20.dp))
                    PanelDialText(if (fresh) "${checkNotNull(weather?.temperature).roundToInt()}°C" else "--°C", color = ink, fontSize = 20.sp,
                        lineHeight = 24.sp, maxLines = 1, modifier = Modifier.testTag("panel-weather-temperature"))
                }
                PanelDialText(panelWeatherStatus(weather, seconds), color = ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("panel-weather-status"))
                val today = days.firstOrNull()?.takeIf { it.date == PanelWeatherSnapshot.dateAtOffset(seconds, weather?.utcOffsetSeconds ?: 0) }
                PanelDialText(today?.let { "${it.minimum.roundToInt()}° / ${it.maximum.roundToInt()}°" } ?: "--° / --°", color = quiet,
                    fontSize = 12.sp, maxLines = 1, modifier = Modifier.testTag("panel-info-today-range"))
            }
            repeat(3) { index ->
                val day = days.getOrNull(index)
                Column(Modifier.weight(1f).fillMaxHeight().border(1.dp, ink).padding(3.dp).testTag("panel-forecast-$index"),
                    verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                    PanelDialText(day?.label(locale) ?: stringResource(R.string.control_panel_forecast_unavailable), color = ink,
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        PanelWeatherGlyph(day?.weatherCode, ink, Modifier.size(18.dp))
                        PanelDialText(day?.let { stringResource(weatherDescription(it.weatherCode)) } ?: "--", color = quiet,
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PanelDialText(day?.let { "${it.minimum.roundToInt()}° / ${it.maximum.roundToInt()}°" } ?: "--° / --°", color = ink,
                        fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelDialText(SimpleDateFormat("EEE  MM/dd", locale).format(now.time), Modifier.weight(0.35f), color = ink, fontSize = 12.sp, maxLines = 1)
            PanelDialText(weather?.resolvedCity ?: options.city.ifBlank { stringResource(R.string.control_panel_dial_no_city) }, Modifier.weight(0.65f), color = ink,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PanelWeatherAttribution(weather, ink, compact = true, showTimezone = true)
    }
}

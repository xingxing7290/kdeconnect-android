/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import org.kde.kdeconnect_tp.R
import kotlin.math.roundToInt

@Composable
internal fun PanelInformationDial(options: PanelIdle, now: Calendar, weather: PanelWeatherSnapshot?, modifier: Modifier) {
    val battery = rememberPanelBatteryState()
    val ink = Color(0xFF202722)
    val quiet = ink.copy(alpha = 0.62f)
    val seconds = now.timeInMillis / 1000
    val fresh = weather?.fresh(seconds) == true && weather.status !in setOf("disabled", "no_city")
    val days = weather?.forecastDays(seconds).orEmpty()
    val locale = panelDisplayLocale()
    BoxWithConstraints(modifier.border(3.dp, ink, RoundedCornerShape(14.dp)).padding(10.dp)) {
        if (maxHeight.value / LocalDensity.current.fontScale < 200f) {
            PanelCompactInformationDial(options, now, weather, battery, ink)
            return@BoxWithConstraints
        }
        val unit = minOf(maxWidth.value / 720f, maxHeight.value / 360f).coerceIn(0.45f, 2f)
        val gap = (8 * unit).dp
        val messageLines = if (maxHeight < 320.dp) 2 else 3
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
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
                Column(Modifier.weight(1.05f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Box(Modifier.fillMaxWidth().weight(0.28f).border(1.dp, ink).padding(gap), contentAlignment = Alignment.CenterStart) {
                        PanelDialText(options.message.ifBlank { stringResource(R.string.control_panel_info_message_hint) }, color = ink,
                            fontSize = (18 * unit).sp, maxLines = messageLines, overflow = TextOverflow.Ellipsis)
                    }
                    Column(Modifier.fillMaxWidth().weight(0.72f).border(1.dp, ink).padding(gap), verticalArrangement = Arrangement.SpaceEvenly) {
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

@Composable
private fun PanelCompactInformationDial(options: PanelIdle, now: Calendar, weather: PanelWeatherSnapshot?, battery: PanelBatteryState, ink: Color) {
    val locale = panelDisplayLocale()
    val seconds = now.timeInMillis / 1000
    val fresh = weather?.fresh(seconds) == true && weather.status !in setOf("disabled", "no_city")
    val days = weather?.forecastDays(seconds).orEmpty()
    val quiet = ink.copy(alpha = 0.62f)
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
        PanelDialText(options.message.ifBlank { stringResource(R.string.control_panel_info_message_hint) }, color = ink,
            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.kde.kdeconnect_tp.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

data class PanelClockAngles(val hour: Float, val minute: Float, val second: Float) {
    companion object {
        fun at(hour: Int, minute: Int, second: Int): PanelClockAngles {
            require(hour in 0..23 && minute in 0..59 && second in 0..59)
            return PanelClockAngles((hour % 12 + minute / 60f + second / 3600f) * 30, (minute + second / 60f) * 6, second * 6f)
        }
    }
}

@Composable
fun PanelDialScreen(options: PanelIdle, connection: PanelConnection, onWake: () -> Unit, weather: PanelWeatherSnapshot? = null) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = Calendar.getInstance()
            delay(1000 - now.timeInMillis % 1000)
        }
    }
    val locale = Locale.getDefault()
    val time = SimpleDateFormat("HH:mm", locale).format(now.time)
    val date = SimpleDateFormat("yyyy/MM/dd  EEEE", locale).format(now.time)
    val light = options.dialId == "info"
    val ink = if (light) Color(0xFF202722) else Color(0xFFF5F7FA)
    val accent = if (light) ink else Color(0xFF85B4FF)
    val wakeDescription = stringResource(R.string.control_panel_dial_wake)
    val snapshot = weather?.takeIf { it.city == options.city.trim() }?.let {
        if (connection != PanelConnection.READY && it.status == "ready") it.copy(status = "unavailable") else it
    }
    val cityLabel = snapshot?.resolvedCity ?: options.city.ifBlank { stringResource(R.string.control_panel_dial_no_city) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(if (light) Color(0xFFECEEE8) else Color.Black)
            .clickable(onClickLabel = wakeDescription, onClick = onWake)
            .semantics { contentDescription = wakeDescription }.testTag("panel-dial-${options.dialId}")
            .safeDrawingPadding().padding(24.dp),
    ) {
        val timeSize = minOf(maxWidth.value * 0.23f, maxHeight.value * 0.56f).sp
        val infoWidth = maxWidth / 3
        val carTimeSize = minOf(maxWidth.value * 0.12f, maxHeight.value * 0.3f).sp
        val inner = Modifier.fillMaxSize().padding(bottom = 25.dp)
        when (options.dialId) {
            "analog" -> {
                PanelAnalogClock(now, inner)
                Text(date, Modifier.align(Alignment.BottomCenter).padding(bottom = 25.dp), color = ink, fontSize = 13.sp)
            }
            "weather" -> Row(inner, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                PanelAnalogClock(now, Modifier.weight(1f).fillMaxHeight(), circular = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(cityLabel, color = ink, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    PanelWeatherReadout(snapshot, now.timeInMillis / 1000, ink, 64.sp)
                    Text(date, color = ink, fontSize = 14.sp)
                    DialMessage(options.message, ink)
                }
            }
            "info" -> Column(inner, verticalArrangement = Arrangement.SpaceEvenly) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(time, color = ink, fontSize = timeSize, fontWeight = FontWeight.Light)
                    Column(Modifier.widthIn(max = infoWidth), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(date, color = ink, fontSize = 20.sp)
                        Text(cityLabel, color = ink, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        PanelWeatherReadout(snapshot, now.timeInMillis / 1000, ink, 28.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().background(Color(0xFFDDE2D7), RoundedCornerShape(4.dp)).padding(16.dp)) { DialMessage(options.message, ink) }
            }
            "car" -> Row(inner, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PanelCarArt(Modifier.weight(1.2f).fillMaxHeight())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(time, color = accent, fontSize = carTimeSize, fontFamily = FontFamily.Monospace)
                    Text(date, color = ink, fontSize = 16.sp)
                    DialMessage(options.message, ink)
                    Text(stringResource(R.string.control_panel_dial_car_art), color = Color(0xFF8391A5), fontSize = 11.sp)
                }
            }
            else -> Row(inner, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(time, color = accent, fontSize = timeSize, fontWeight = FontWeight.Light, maxLines = 1)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(date, color = ink, fontSize = 20.sp)
                    if (options.city.isNotBlank()) PanelWeatherReadout(snapshot, now.timeInMillis / 1000, ink, 28.sp)
                    DialMessage(options.message, ink)
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(wakeDescription, color = if (light) Color(0xFF626C62) else Color(0xFF8391A5), fontSize = 11.sp)
            if (connection != PanelConnection.READY) Text(stringResource(R.string.control_panel_offline), color = Color(0xFFC06B66), fontSize = 11.sp)
        }
    }
}

@Composable
private fun PanelWeatherReadout(snapshot: PanelWeatherSnapshot?, now: Long, ink: Color, temperatureSize: TextUnit) {
    val fresh = snapshot?.fresh(now) == true && snapshot.status !in setOf("disabled", "no_city")
    val status = when {
        snapshot?.status == "no_city" -> stringResource(R.string.control_panel_dial_no_city)
        snapshot?.status == "disabled" -> stringResource(R.string.control_panel_weather_disabled)
        snapshot?.hasObservation == true && !fresh -> stringResource(R.string.control_panel_weather_stale)
        snapshot?.status == "loading" -> stringResource(R.string.control_panel_weather_loading)
        snapshot?.status == "ready" && fresh -> stringResource(weatherDescription(snapshot.weatherCode))
        else -> stringResource(R.string.control_panel_dial_weather_unavailable)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (fresh) "${checkNotNull(snapshot.temperature).roundToInt()}°C" else "--°C", color = ink, fontSize = temperatureSize, fontWeight = FontWeight.Light, modifier = Modifier.testTag("panel-weather-temperature"))
        Text(if (fresh && snapshot.status != "ready") stringResource(R.string.control_panel_weather_cached, status) else status, color = ink, fontSize = 13.sp, modifier = Modifier.testTag("panel-weather-status"))
        Text(stringResource(R.string.control_panel_weather_source), color = ink.copy(alpha = 0.65f), fontSize = 10.sp)
        if (snapshot?.hasObservation == true) {
            val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            Text(stringResource(R.string.control_panel_weather_updated,
                formatter.format(Date(checkNotNull(snapshot.observedAt) * 1000)),
                formatter.format(Date(checkNotNull(snapshot.fetchedAt) * 1000))), color = ink.copy(alpha = 0.65f), fontSize = 10.sp, maxLines = 2)
        }
    }
}

private fun weatherDescription(code: Int?) = when (code) {
    0 -> R.string.control_panel_weather_clear
    1, 2 -> R.string.control_panel_weather_partly_cloudy
    3 -> R.string.control_panel_weather_overcast
    45, 48 -> R.string.control_panel_weather_fog
    51, 53, 55 -> R.string.control_panel_weather_drizzle
    56, 57, 66, 67 -> R.string.control_panel_weather_freezing_rain
    61, 63, 65 -> R.string.control_panel_weather_rain
    71, 73, 75, 77, 85, 86 -> R.string.control_panel_weather_snow
    80, 81, 82 -> R.string.control_panel_weather_showers
    95, 96, 99 -> R.string.control_panel_weather_thunderstorm
    else -> R.string.control_panel_dial_weather_unavailable
}

@Composable
private fun DialMessage(message: String, color: Color) {
    if (message.isNotBlank()) Text(message, color = color, fontSize = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun PanelAnalogClock(now: Calendar, modifier: Modifier, circular: Boolean = false) {
    val angles = PanelClockAngles.at(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND))
    Canvas(modifier.semantics { contentDescription = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now.time) }) {
        val center = Offset(size.width / 2, size.height / 2)
        val ry = size.height * 0.43f
        val rx = if (circular) minOf(ry, size.width * 0.45f) else size.width * 0.46f
        val vertical = if (circular) rx else ry
        fun point(degrees: Float, scale: Float): Offset {
            val angle = Math.toRadians(degrees.toDouble())
            return center + Offset(sin(angle).toFloat() * rx * scale, -cos(angle).toFloat() * vertical * scale)
        }
        repeat(60) { index ->
            val major = index % 5 == 0
            drawLine(if (major) Color(0xFFD7DFE7) else Color(0xFF5E6671), point(index * 6f, if (major) 0.86f else 0.94f), point(index * 6f, 1f), if (major) 2.dp.toPx() else 1.dp.toPx())
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER; textSize = minOf(rx, vertical) * 0.2f }
        for (hour in listOf(12, 3, 6, 9)) {
            val position = point(hour * 30f, 0.71f)
            drawContext.canvas.nativeCanvas.drawText(hour.toString(), position.x, position.y - (paint.ascent() + paint.descent()) / 2, paint)
        }
        drawLine(Color.White, point(angles.hour, -0.06f), point(angles.hour, 0.48f), 5.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White, point(angles.minute, -0.08f), point(angles.minute, 0.72f), 3.dp.toPx(), StrokeCap.Round)
        drawLine(Color(0xFFDBA353), point(angles.second, -0.15f), point(angles.second, 0.88f), 1.dp.toPx(), StrokeCap.Round)
        drawCircle(Color(0xFFDBA353), 4.dp.toPx(), center)
    }
}

@Composable
private fun PanelCarArt(modifier: Modifier) {
    Canvas(modifier) {
        val left = size.width * 0.06f
        val top = size.height * 0.36f
        val width = size.width * 0.88f
        val height = size.height * 0.28f
        drawRoundRect(Color(0xFF263D56), Offset(left, top), Size(width, height), CornerRadius(height * 0.28f))
        drawRoundRect(Color(0xFF94C3ED), Offset(left + width * 0.24f, top - height * 0.42f), Size(width * 0.49f, height * 0.65f), CornerRadius(height * 0.2f), style = Stroke(2.dp.toPx()))
        listOf(0.2f, 0.8f).forEach { part ->
            val center = Offset(left + width * part, top + height * 0.89f)
            drawCircle(Color(0xFF111720), height * 0.29f, center)
            drawCircle(Color(0xFF8AB9E8), height * 0.15f, center, style = Stroke(3.dp.toPx()))
        }
        drawLine(Color(0xFF5B9ACF), Offset(left, top + height * 1.28f), Offset(left + width, top + height * 1.28f), 1.dp.toPx())
    }
}

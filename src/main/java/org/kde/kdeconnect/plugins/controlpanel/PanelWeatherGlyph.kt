/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun PanelWeatherGlyph(code: Int?, ink: Color, modifier: Modifier) {
    val description = stringResource(weatherDescription(code))
    Canvas(modifier.semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        val line = minOf(w, h) * 0.035f
        if (code == null) {
            drawLine(ink.copy(alpha = 0.4f), Offset(w * 0.25f, h * 0.5f), Offset(w * 0.75f, h * 0.5f), line, StrokeCap.Round)
            return@Canvas
        }
        if (code in 0..2) {
            val center = Offset(w * 0.4f, h * 0.35f)
            val radius = minOf(w, h) * 0.16f
            drawCircle(ink, radius, center, style = Stroke(line))
            repeat(8) {
                val angle = it * Math.PI / 4
                val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                drawLine(ink, center + direction * radius * 1.45f, center + direction * radius * 1.8f, line, StrokeCap.Round)
            }
        }
        if (code != 0) {
            val cloud = Path().apply {
                moveTo(w * 0.2f, h * 0.68f)
                cubicTo(w * 0.02f, h * 0.67f, w * 0.08f, h * 0.4f, w * 0.28f, h * 0.45f)
                cubicTo(w * 0.3f, h * 0.13f, w * 0.7f, h * 0.12f, w * 0.72f, h * 0.44f)
                cubicTo(w * 0.94f, h * 0.37f, w * 0.99f, h * 0.68f, w * 0.79f, h * 0.68f)
                close()
            }
            drawPath(cloud, ink, style = Stroke(line))
        }
        when (code) {
            45, 48 -> repeat(2) { index -> drawLine(ink, Offset(w * 0.2f, h * (0.8f + index * 0.1f)), Offset(w * 0.8f, h * (0.8f + index * 0.1f)), line, StrokeCap.Round) }
            71, 73, 75, 77, 85, 86 -> for (x in listOf(0.3f, 0.7f)) {
                val center = Offset(w * x, h * 0.85f)
                repeat(3) { index ->
                    val angle = index * Math.PI / 3
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat()) * minOf(w, h) * 0.06f
                    drawLine(ink, center - direction, center + direction, line * 0.7f, StrokeCap.Round)
                }
            }
            95, 96, 99 -> {
                val bolt = Path().apply { moveTo(w * 0.55f, h * 0.72f); lineTo(w * 0.4f, h * 0.86f); lineTo(w * 0.57f, h * 0.86f); lineTo(w * 0.46f, h * 0.99f) }
                drawPath(bolt, ink, style = Stroke(line))
            }
            in 51..82 -> for (x in listOf(0.3f, 0.5f, 0.7f)) drawLine(ink, Offset(w * x, h * 0.79f), Offset(w * (x - 0.06f), h * 0.92f), line, StrokeCap.Round)
        }
    }
}

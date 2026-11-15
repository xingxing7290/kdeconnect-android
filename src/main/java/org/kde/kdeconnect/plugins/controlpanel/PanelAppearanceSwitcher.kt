/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect_tp.R
import kotlin.math.cos
import kotlin.math.sin

/** Header button that switches the phone-side control panel between light, dark and system mode. */
@Composable
fun PanelAppearanceSwitcher(
    mode: PanelAppearanceMode,
    onChange: (PanelAppearanceMode) -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.control_panel_appearance_switch)
    Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("panel-appearance-button").semantics { contentDescription = description },
        ) {
            AppearanceIcon(mode, tint)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag("panel-appearance-menu"),
        ) {
            PanelAppearanceMode.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    leadingIcon = { AppearanceIcon(option, tint, tagged = false) },
                    onClick = { expanded = false; onChange(option) },
                    modifier = Modifier.testTag("panel-appearance-${option.stored}"),
                )
            }
        }
    }
}

@Composable
private fun PanelAppearanceMode.label(): String = stringResource(
    when (this) {
        PanelAppearanceMode.SYSTEM -> R.string.control_panel_appearance_system
        PanelAppearanceMode.LIGHT -> R.string.control_panel_appearance_light
        PanelAppearanceMode.DARK -> R.string.control_panel_appearance_dark
    }
)

/** Drawn locally: material-icons-core does not ship brightness/theme glyphs. */
@Composable
private fun AppearanceIcon(mode: PanelAppearanceMode, tint: Color, modifier: Modifier = Modifier, tagged: Boolean = true) {
    val canvasModifier = modifier.size(24.dp).run {
        if (tagged) testTag("panel-appearance-icon-${mode.stored}") else this
    }
    Canvas(canvasModifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = minOf(size.width, size.height) * 0.26f
        when (mode) {
            PanelAppearanceMode.LIGHT -> {
                repeat(8) { index ->
                    val angle = Math.toRadians((index * 45).toDouble())
                    val dx = cos(angle).toFloat()
                    val dy = sin(angle).toFloat()
                    drawLine(tint,
                        center + Offset(dx * radius * 1.45f, dy * radius * 1.45f),
                        center + Offset(dx * radius * 2.0f, dy * radius * 2.0f),
                        2.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(tint, radius, center)
            }
            PanelAppearanceMode.DARK -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = tint.toArgb()
                    style = Paint.Style.FILL
                }
                val moon = android.graphics.Path().apply {
                    addCircle(center.x, center.y, radius * 1.25f, android.graphics.Path.Direction.CW)
                }
                val cut = android.graphics.Path().apply {
                    addCircle(center.x + radius * 0.62f, center.y - radius * 0.35f, radius * 1.08f, android.graphics.Path.Direction.CW)
                }
                moon.op(cut, android.graphics.Path.Op.DIFFERENCE)
                drawContext.canvas.nativeCanvas.drawPath(moon, paint)
            }
            PanelAppearanceMode.SYSTEM -> {
                val outer = radius * 1.25f
                drawCircle(tint.copy(alpha = 0.18f), outer, center)
                drawArc(tint, 270f, 180f, true,
                    topLeft = Offset(center.x - outer, center.y - outer), size = Size(outer * 2, outer * 2))
                drawCircle(tint, outer, center, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import org.kde.kdeconnect.plugins.systemvolume.Sink
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin
import org.kde.kdeconnect_tp.R
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ControlPanelScreen(
    deviceName: String,
    session: PanelSession?,
    plugin: ControlPanelPlugin?,
    volume: SystemVolumePlugin?,
    onBack: () -> Unit,
) {
    val empty = remember { MutableStateFlow(PanelSessionState()) }
    val state by (session?.state ?: empty).collectAsState()
    val config = state.config
    val ready = plugin != null && state.connection == PanelConnection.READY
    val background = config?.appearance?.background?.let { Color(android.graphics.Color.parseColor(it)) } ?: Color(0xFFF4F6FA)
    val page = config?.page(state.activePageId) ?: config?.pages?.firstOrNull()
    val pages = if (config?.mode == "single") config.pages.filter { it.id == config.homePageId } else config?.pages.orEmpty()
    fun movePage(direction: Int) {
        val index = pages.indexOfFirst { it.id == page?.id }
        pages.getOrNull(index + direction)?.let { plugin?.selectPage(it.id) }
    }
    val swipe = Modifier.pointerInput(config?.revision, page?.id, ready) {
        if (!ready || pages.size <= 1) return@pointerInput
        var distance = 0f
        val end = { if (abs(distance) > 48.dp.toPx()) movePage(if (distance < 0) 1 else -1); distance = 0f }
        if (config?.appearance?.swipeDirection == "vertical") {
            detectVerticalDragGestures(onDragStart = { distance = 0f }, onDragEnd = end, onDragCancel = { distance = 0f }) { change, amount ->
                change.consume(); distance += amount
            }
        } else {
            detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = end, onDragCancel = { distance = 0f }) { change, amount ->
                change.consume(); distance += amount
            }
        }
    }
    Column(Modifier.fillMaxSize().background(background).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.control_panel_back)) }
            Text(deviceName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF25334A))
            if (!ready) {
                Text(
                    when (state.connection) {
                        PanelConnection.SYNCING -> stringResource(R.string.control_panel_syncing)
                        PanelConnection.ERROR -> state.error
                        else -> stringResource(R.string.control_panel_offline)
                    }, color = Color(0xFFB3261E), fontSize = 12.sp,
                )
            }
            IconButton(onClick = { plugin?.synchronize() }, enabled = plugin != null) {
                Icon(Icons.Default.Refresh, stringResource(R.string.control_panel_retry))
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth().then(swipe), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(6) { column ->
                            val index = row * 6 + column
                            val button = page?.slots?.getOrNull(index)
                            val canActivate = ready && button != null && button.id !in state.pendingButtons
                            val slotDescription = button?.name ?: stringResource(R.string.control_panel_empty_slot, index + 1)
                            Surface(
                                onClick = { button?.let { plugin?.activate(it.id) } },
                                enabled = canActivate,
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("panel-slot-$index")
                                    .alpha(if (ready || button == null) 1f else 0.5f)
                                    .semantics { contentDescription = slotDescription; if (!canActivate) disabled() },
                                shape = RoundedCornerShape(12.dp),
                                color = if (button == null) Color(0x338292AC) else Color.White.copy(alpha = 0.86f),
                                shadowElevation = if (button == null) 0.dp else 1.dp,
                            ) {
                                Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    if (button == null) Text("+", color = Color(0xFF9BA5B4), fontSize = 28.sp)
                                    else {
                                        if (button.id in state.pendingButtons) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                                        else Icon(painterResource(buttonIcon(button.kind)), null, Modifier.size(30.dp), tint = Color(0xFF4177C3))
                                        if (config?.appearance?.showLabels == true) Text(button.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = Color(0xFF25334A))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (config?.appearance?.showVolume == true) PanelVolume(volume, ready)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            pages.forEachIndexed { index, item ->
                TextButton(onClick = { plugin?.selectPage(item.id) }, enabled = ready) {
                    Text("${index + 1}  ${item.name}", color = if (item.id == page?.id) Color(0xFF286FE0) else Color(0xFF69778B), maxLines = 1)
                }
            }
            if (page?.slots?.all { it == null } != false) Text(stringResource(R.string.control_panel_empty), fontSize = 12.sp, color = Color(0xFF69778B))
        }
        state.lastResult?.let { result ->
            if (!result.success) Text(stringResource(R.string.control_panel_failed, result.error), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

private fun buttonIcon(kind: String) = when (kind) {
    "website" -> R.drawable.ic_baseline_web_24
    "folder" -> R.drawable.panel_folder
    "file" -> R.drawable.panel_file
    "shortcut" -> R.drawable.ic_action_keyboard_24dp
    else -> R.drawable.run_command_plugin_icon_24dp
}

private data class VolumeState(val sink: Sink? = null, val volume: Int = 0, val maximum: Int = 100, val muted: Boolean = false)

@Composable
private fun PanelVolume(plugin: SystemVolumePlugin?, enabled: Boolean) {
    var state by remember(plugin) { mutableStateOf(VolumeState()) }
    DisposableEffect(plugin) {
        val main = Handler(Looper.getMainLooper())
        var disposed = false
        var watched = emptyList<Sink>()
        fun refresh() {
            if (disposed) return
            val sinks = plugin?.sinks?.toList().orEmpty()
            val sink = sinks.firstOrNull { it.isDefault } ?: sinks.firstOrNull()
            state = VolumeState(sink, sink?.volume ?: 0, sink?.maxVolume?.coerceAtLeast(1) ?: 100, sink?.mute ?: false)
        }
        val update = object : Sink.UpdateListener {
            override fun updateSink(sink: Sink) { main.post { refresh() } }
        }
        fun bind() {
            if (disposed) return
            watched.forEach { it.removeListener(update) }
            watched = plugin?.sinks?.toList().orEmpty()
            watched.forEach { it.addListener(update) }
            refresh()
        }
        val list = object : SystemVolumePlugin.SinkListener {
            override fun sinksChanged() { main.post { bind() } }
        }
        plugin?.addSinkListener(list)
        bind()
        onDispose {
            disposed = true
            plugin?.removeSinkListener(list)
            watched.forEach { it.removeListener(update) }
        }
    }
    var dragged by remember(state.sink?.name) { mutableStateOf<Float?>(null) }
    Column(Modifier.width(56.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_volume), stringResource(R.string.control_panel_volume), Modifier.size(22.dp), tint = Color(0xFF69778B))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Slider(
                value = dragged ?: state.volume.toFloat(),
                onValueChange = { dragged = it },
                onValueChangeFinished = { dragged?.let { value -> state.sink?.let { plugin?.sendVolume(it.name, value.roundToInt()) } }; dragged = null },
                valueRange = 0f..state.maximum.toFloat(),
                enabled = enabled && state.sink != null,
                modifier = Modifier.requiredWidth(maxHeight).rotate(-90f),
            )
        }
        IconButton(onClick = { state.sink?.let { plugin?.sendMute(it.name, !state.muted) } }, enabled = enabled && state.sink != null) {
            Icon(painterResource(if (state.muted) R.drawable.ic_volume_mute else R.drawable.ic_volume), stringResource(if (state.muted) R.string.control_panel_unmute else R.string.control_panel_mute), tint = Color(0xFF69778B))
        }
    }
}

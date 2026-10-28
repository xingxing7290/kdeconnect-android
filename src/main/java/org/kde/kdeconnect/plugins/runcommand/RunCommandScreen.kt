package org.kde.kdeconnect.plugins.runcommand

import android.content.res.Configuration
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.KdeTopAppBar
import org.kde.kdeconnect_tp.R
import kotlin.time.Duration.Companion.seconds

@Composable
fun RunCommandScreen(
    plugin: RunCommandPlugin,
    device: Device,
    commandList: SnapshotStateList<CommandEntry>,
    onBackPressedDispatcher: OnBackPressedDispatcher,
    onCopyUrlToClipboard: (CommandEntry) -> Unit,
    onUpdate: () -> Unit,
    onOrderChanged: (List<String>) -> Unit,
    onOpenFullscreen: () -> Unit,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val outputList = remember { plugin.output }

    DisposableEffect(plugin) {
        val callback = RunCommandPlugin.CommandsChangedCallback {
            scope.launch(Dispatchers.Main.immediate) {
                onUpdate()
            }
        }
        plugin.addCommandsUpdatedCallback(callback)

        onDispose {
            plugin.removeCommandsUpdatedCallback(callback)
        }
    }

    val showOutputCard = commandList.isNotEmpty() &&
        device.canSendPacketType(RunCommandPlugin.PACKET_TYPE_RUNCOMMAND_OUTPUT)

    KdeTheme(context) {
        Scaffold(
            modifier = Modifier.safeDrawingPadding(),
            topBar = {
                RunCommandAppBar(
                    subTitle = device.name,
                    onBackPressedDispatcher = onBackPressedDispatcher,
                    showClearOutput = showOutputCard,
                    onClearOutput = { plugin.clearOutput() },
                    onOpenFullscreen = onOpenFullscreen,
                )
            },
            floatingActionButton = {
                if (plugin.canAddCommand()) {
                    FloatingActionButton(onClick = {
                        plugin.sendSetupPacket()
                        showDialog = true
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_action_image_edit_24dp),
                            stringResource(R.string.add_command)
                        )
                    }
                }
            },
        ) {
            if (showDialog) {
                AlertDialog(
                    title = {
                        Text(
                            stringResource(R.string.add_command),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        Text(stringResource(R.string.add_command_description))
                    },
                    onDismissRequest = {
                        showDialog = false
                        onUpdate()
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showDialog = false
                            onUpdate()
                        }) {
                            Text(stringResource(R.string.ok))
                        }
                    },
                    dismissButton = {},
                )
            }

            if (!commandList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(it)
                        .fillMaxSize()
                ) {
                    if (showOutputCard) {
                        OutputCard(outputList, plugin)
                    }
                    CommandGrid(
                        commands = commandList,
                        onCommandClick = { command -> plugin.runCommand(command.key) },
                        onMoreClick = onCopyUrlToClipboard,
                        onOrderChanged = onOrderChanged,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(it)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var text = stringResource(R.string.addcommand_explanation)
                    if (!plugin.canAddCommand()) {
                        text += "\n" + stringResource(R.string.addcommand_explanation2)
                    }
                    Text(text)
                }
            }
        }
    }
}

@Composable
private fun CommandGrid(
    commands: List<CommandEntry>,
    onCommandClick: (CommandEntry) -> Unit,
    onMoreClick: (CommandEntry) -> Unit,
    onOrderChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val spanCount = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        3
    } else {
        maxOf(3, configuration.screenWidthDp / 120)
    }
    val currentOnCommandClick by rememberUpdatedState(onCommandClick)
    val currentOnMoreClick by rememberUpdatedState(onMoreClick)
    val currentOnOrderChanged by rememberUpdatedState(onOrderChanged)

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = GridLayoutManager(context, spanCount)
                clipToPadding = false
                val commandAdapter = CommandEntryAdapter(
                    commands = commands.toMutableList(),
                    onItemClick = { command -> currentOnCommandClick(command) },
                    onMoreClick = { command -> currentOnMoreClick(command) },
                )
                adapter = commandAdapter

                ItemTouchHelper(
                    object : ItemTouchHelper.SimpleCallback(
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                        0,
                    ) {
                        override fun onMove(
                            recyclerView: RecyclerView,
                            viewHolder: RecyclerView.ViewHolder,
                            target: RecyclerView.ViewHolder,
                        ): Boolean {
                            val from = viewHolder.bindingAdapterPosition
                            val to = target.bindingAdapterPosition
                            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                                return false
                            }
                            commandAdapter.moveItem(from, to)
                            currentOnOrderChanged(commandAdapter.getCommandKeys())
                            return true
                        }

                        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
                    }
                ).attachToRecyclerView(this)
            }
        },
        update = { recyclerView ->
            (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = spanCount
            (recyclerView.adapter as? CommandEntryAdapter)?.setCommands(commands)
        },
    )
}

@Composable
private fun OutputCard(
    outputList: SnapshotStateList<RunCommandOutput>,
    plugin: RunCommandPlugin
) {
    val state = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val commandRunning by plugin.commandRunning
    var showStopButton by remember { mutableStateOf(false) }

    LaunchedEffect(commandRunning) {
        if (commandRunning) {
            delay(1.seconds)
            showStopButton = true
        } else {
            showStopButton = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        if (outputList.isNotEmpty()) {
            Box {
                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 15.dp, vertical = 5.dp)
                        .fillMaxWidth(),
                    state = state
                ) {
                    items(outputList) { text ->
                        Text(
                            modifier = Modifier
                                .fillMaxWidth(),
                            text = text.string,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (text.isCommand) FontWeight.ExtraBold else FontWeight.Normal,
                            color = when (text.commandStatus) {
                                RunCommandStatus.COMMAND_SUCCESSFUL -> Color(0xFF2E7D32)
                                RunCommandStatus.COMMAND_FAILED -> MaterialTheme.colorScheme.error
                                else -> Color.Unspecified
                            }
                        )
                    }
                }
                if (showStopButton) {
                    Column(
                        modifier = Modifier.padding(5.dp).fillMaxSize(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        IconButton(onClick = { plugin.sendStop() }) {
                            CircularProgressIndicator()
                            Icon(
                                painterResource(R.drawable.ic_stop),
                                stringResource(R.string.runcommand_stop)
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.runcommand_output_no_output),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.runcommand_output_no_output_desc),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    LaunchedEffect(outputList.size) {
        if (outputList.isNotEmpty()) {
            coroutineScope.launch {
                state.animateScrollToItem(outputList.size - 1)
            }
        }
    }
}

@Composable
fun RunCommandAppBar(
    subTitle: String,
    onBackPressedDispatcher: OnBackPressedDispatcher,
    showClearOutput: Boolean,
    onClearOutput: () -> Unit,
    onOpenFullscreen: () -> Unit,
) {
    KdeTopAppBar(
        title = stringResource(R.string.pref_plugin_runcommand),
        subTitle = subTitle,
        navIconOnClick = { onBackPressedDispatcher.onBackPressed() },
        navIconDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
        actions = {
            IconButton(onClick = onOpenFullscreen) {
                Icon(
                    painterResource(R.drawable.ic_open_in_full_24dp),
                    stringResource(R.string.presenter_fullscreen),
                )
            }
            if (showClearOutput) {
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.extra_options))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.runcommand_clear_output)) },
                        onClick = {
                            menuExpanded = false
                            onClearOutput()
                        },
                    )
                }
            }
        },
    )
}

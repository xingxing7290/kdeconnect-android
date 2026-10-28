/*
 * SPDX-FileCopyrightText: 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2026 Johann Specht <sajeg.dev@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.runcommand

import android.content.ClipData
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect_tp.R


class RunCommandActivity : AppCompatActivity() {

    val commandList = mutableStateListOf<CommandEntry>()

    private lateinit var deviceId: String
    private lateinit var sharedPreferences: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("RunCommandActivity", "Launched")
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)!!
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val device = KdeConnect.getInstance().getDevice(deviceId)
        val plugin = device?.getPlugin<RunCommandPlugin>()
        if (plugin == null) {
            finish()
            return
        }

        replaceCommands(plugin)


        setContent {
            val clipboardManager = LocalClipboard.current

            RunCommandScreen(
                plugin = plugin,
                device = device,
                commandList = commandList,
                onBackPressedDispatcher = onBackPressedDispatcher,
                onCopyUrlToClipboard = {
                    copyCommandToClipboard(device, it, clipboardManager)
                },
                onUpdate = {
                    updateList()
                },
                onOrderChanged = { keys -> reorderCommands(keys) },
                onOpenFullscreen = {
                    startActivity(
                        Intent(this, FullScreenRunCommandActivity::class.java)
                            .putExtra(EXTRA_DEVICE_ID, deviceId)
                    )
                },
            )
        }
    }

    private fun copyCommandToClipboard(
        device: Device,
        command: CommandEntry,
        clipboardManager: Clipboard
    ) {
        val url = "kdeconnect://runcommand/${device.deviceId}/${command.key}"
        val clipData = ClipData.newPlainText("Command", url)

        CoroutineScope(Dispatchers.IO).launch {
            clipboardManager.setClipEntry(clipData.toClipEntry())
        }
        val toast = Toast.makeText(
            this,
            R.string.clipboard_toast,
            Toast.LENGTH_SHORT
        )
        toast.show()
    }

    private fun updateList() {
        val plugin = KdeConnect.getInstance().getDevicePlugin<RunCommandPlugin>(deviceId) ?: return
        replaceCommands(plugin)
    }

    private fun replaceCommands(plugin: RunCommandPlugin) {
        val entries = plugin.commandList
            .map(::CommandEntry)
            .sortedBy { it.name.lowercase() }
        val entriesByKey = entries.associateBy { it.key }
        val ordered = buildList {
            loadSavedOrder().forEach { key -> entriesByKey[key]?.let(::add) }
            addAll(entries.filter { entry -> none { it.key == entry.key } })
        }

        commandList.clear()
        commandList.addAll(ordered)
        saveOrder(commandList.map { it.key })
    }

    private fun reorderCommands(keys: List<String>) {
        val commandsByKey = commandList.associateBy { it.key }
        val reordered = buildList {
            keys.forEach { key -> commandsByKey[key]?.let(::add) }
            addAll(commandList.filter { command -> none { it.key == command.key } })
        }

        commandList.clear()
        commandList.addAll(reordered)
        saveOrder(commandList.map { it.key })
    }

    private fun loadSavedOrder(): List<String> {
        val raw = sharedPreferences.getString(PREF_KEY_ORDER_PREFIX + deviceId, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.getString(index) }
        }.getOrDefault(emptyList())
    }

    private fun saveOrder(keys: List<String>) {
        val array = JSONArray()
        keys.forEach(array::put)
        sharedPreferences.edit()
            .putString(PREF_KEY_ORDER_PREFIX + deviceId, array.toString())
            .apply()
    }

    override fun onResume() {
        super.onResume()
        val plugin =
            KdeConnect.getInstance().getDevicePlugin<RunCommandPlugin>(deviceId)
        if (plugin == null) {
            finish()
            return
        }
    }

    companion object {
        const val EXTRA_DEVICE_ID = "deviceId"
        private const val PREF_KEY_ORDER_PREFIX = "runcommand_order_"
    }
}


enum class RunCommandStatus {
    STDERR,
    STDOUT,
    COMMAND_RUNNING,
    COMMAND_SUCCESSFUL,
    COMMAND_FAILED,
}

data class RunCommandOutput(
    val commandStatus: RunCommandStatus,
    val string: String,
    val id: Int,
) {
    val isCommand: Boolean
        get() = commandStatus == RunCommandStatus.COMMAND_RUNNING || commandStatus == RunCommandStatus.COMMAND_SUCCESSFUL || commandStatus == RunCommandStatus.COMMAND_FAILED
}

/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.mpris.MprisPlugin
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect_tp.R

@Composable
fun PluginsScreen(
    pluginsWithButtons: List<Plugin.PluginUiButton>,
    pluginsNeedPermissions: List<Plugin>,
    pluginsNeedOptionalPermissions: List<Plugin>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit,
    action: (plugin: Plugin) -> Unit
) {
    PluginsScreenContent(
        pluginsWithButtons = pluginsWithButtons,
        pluginsNeedPermissions = pluginsNeedPermissions,
        pluginsNeedOptionalPermissions = pluginsNeedOptionalPermissions,
        onButtonClick = onButtonClick,
        action = action
    )
}

@Composable
private fun PluginsScreenContent(
    pluginsWithButtons: List<Plugin.PluginUiButton>,
    pluginsNeedPermissions: List<Plugin>,
    pluginsNeedOptionalPermissions: List<Plugin>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit,
    action: (plugin: Plugin) -> Unit
) {
    Surface {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            PluginActionsList(
                buttons = pluginsWithButtons,
                onButtonClick = onButtonClick
            )
            Spacer(modifier = Modifier.padding(vertical = 8.dp))
            if (pluginsNeedPermissions.isNotEmpty()) {
                PluginsWithoutPermissions(
                    title = stringResource(id = R.string.plugins_need_permission),
                    plugins = pluginsNeedPermissions,
                    action = action
                )
                Spacer(modifier = Modifier.padding(vertical = 2.dp))
            }
            if (pluginsNeedOptionalPermissions.isNotEmpty()) {
                PluginsWithoutPermissions(
                    title = stringResource(id = R.string.plugins_need_optional_permission),
                    plugins = pluginsNeedOptionalPermissions,
                    action = action
                )
            }
        }
    }
}

@Composable
private fun PluginActionsList(
    buttons: List<Plugin.PluginUiButton>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        buttons.forEachIndexed { index, button ->
            PluginAction(
                button = button,
                onClick = { onButtonClick(button) }
            )
            if (index != buttons.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun PluginAction(
    button: Plugin.PluginUiButton,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { role = Role.Button },
        leadingContent = {
            Icon(
                painter = painterResource(id = button.iconRes),
                contentDescription = null
            )
        },
        headlineContent = {
            Text(
                text = button.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
    )
}

@Composable
private fun PluginsWithoutPermissions(
    title: String,
    plugins: Collection<Plugin>,
    action: (plugin: Plugin) -> Unit
) {
    Text(
        text = title,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { heading() }
    )
    plugins.forEach { plugin ->
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { action(plugin) }
                .semantics { role = Role.Button },
            headlineContent = {
                Text(
                    text = plugin.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}

@KdeThemePreviews
@Composable
private fun PluginsScreenPreview() {
    KdeTheme(context = LocalContext.current) {
        val pluginsWithButtons = listOf(
            MprisPlugin(),
            RunCommandPlugin(),
            PresenterPlugin()
        )

        pluginsWithButtons.forEach { plugin ->
            plugin.setContext(
                context = LocalContext.current,
                device = null
            )
        }
        PluginsScreenContent(
            pluginsWithButtons = pluginsWithButtons.flatMap { plugin -> plugin.getUiButtons() },
            pluginsNeedPermissions = emptyList(),
            pluginsNeedOptionalPermissions = emptyList(),
            onButtonClick = { /* Do nothing */ },
            action = { /* Do nothing */ }
        )
    }
}

/*
 * SPDX-FileCopyrightText: 2016 Thomas Posch <kdeconnect@online.posch.name>
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.runcommand

class CommandEntry(info: CommandInfo) {
    val key: String = info.key
    val name: String = info.name
    val command: String = info.command
}

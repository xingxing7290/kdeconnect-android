/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.sftp.saf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 26, 37])
class SafAttributeCompatibilityTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun paths(): Pair<SafFileSystemProvider, SafFileSystem> {
        val provider = SafFileSystemProvider(context, mutableMapOf())
        return provider to SafFileSystem(provider, provider.roots, context)
    }

    @Test fun rootBasicAndPosixViewsKeepTheirExistingReadOnlyContract() {
        val (provider, fileSystem) = paths()
        val root = SafPath.newRootPath(fileSystem)
        val basic = requireNotNull(provider.getFileAttributeView(root, BasicFileAttributeView::class.java))
        assertEquals("basic", basic.name())
        assertTrue(basic.readAttributes().isDirectory)
        assertEquals(0L, basic.readAttributes().size())
        assertThrows(IOException::class.java) { basic.setTimes(null, null, null) }
        val posix = requireNotNull(provider.getFileAttributeView(root, PosixFileAttributeView::class.java))
        assertEquals("posix", posix.name())
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE), posix.readAttributes().permissions())
        assertThrows(IOException::class.java) { posix.setPermissions(mutableSetOf()) }
        assertNull(provider.getFileAttributeView(root, DosFileAttributeView::class.java))
    }

    @Test fun missingDocumentViewsStillReportMissingFilesInsteadOfInventedAttributes() {
        val (provider, fileSystem) = paths()
        val path = SafPath(fileSystem, null, "/", listOf("missing"))
        val basic = requireNotNull(provider.getFileAttributeView(path, BasicFileAttributeView::class.java))
        val posix = requireNotNull(provider.getFileAttributeView(path, PosixFileAttributeView::class.java))
        assertEquals("basic", basic.name())
        assertEquals("posix", posix.name())
        assertThrows(NoSuchFileException::class.java) { basic.readAttributes() }
        assertThrows(NoSuchFileException::class.java) { posix.readAttributes() }
        assertNull(provider.getFileAttributeView(path, DosFileAttributeView::class.java))
    }
}

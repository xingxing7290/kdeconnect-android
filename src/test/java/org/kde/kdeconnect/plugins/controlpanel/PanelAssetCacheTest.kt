/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class PanelAssetCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun png(color: Int, width: Int = 256): ByteArray {
        val image = Bitmap.createBitmap(width, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it); image.recycle() }.toByteArray()
    }
    @Test fun verifiedIconsSurviveFreshCacheInstanceWithoutNetwork() {
        val directory = temporary.newFolder()
        val cache = PanelAssetCache(directory)
        val bytes = png(Color.BLUE)
        val id = PanelAssetCache.hash(bytes)
        assertTrue(cache.accept(id, bytes))
        assertTrue(cache.contains(id))
        assertEquals(256, cache.bitmap(id)?.width)
        val offline = PanelAssetCache(directory)
        assertEquals(256, offline.bitmap(id)?.height)
        assertEquals(Color.BLUE, offline.bitmap(id)?.getPixel(128, 128))
    }
    @Test fun wrongHashSizeTypeAndDimensionsAreRejected() {
        val directory = temporary.newFolder()
        val cache = PanelAssetCache(directory)
        val image = png(Color.RED)
        assertFalse(cache.accept("a".repeat(64), image))
        assertFalse(cache.accept("../escape", image))
        val huge = ByteArray(PanelAssetCache.MAX_BYTES + 1)
        assertFalse(cache.accept(PanelAssetCache.hash(huge), huge))
        val oversized = png(Color.GREEN, 257)
        assertFalse(cache.accept(PanelAssetCache.hash(oversized), oversized))
        val invalid = "not png".toByteArray()
        assertFalse(cache.accept(PanelAssetCache.hash(invalid), invalid))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun corruptedOrMissingFilesAreFetchableAgain() {
        val directory = temporary.newFolder()
        val bytes = png(Color.CYAN)
        val id = PanelAssetCache.hash(bytes)
        val cache = PanelAssetCache(directory)
        assertTrue(cache.accept(id, bytes))
        File(directory, "$id.png").writeText("broken")
        val restarted = PanelAssetCache(directory)
        assertFalse(restarted.contains(id))
        assertNull(restarted.bitmap(id))
        assertTrue(restarted.accept(id, bytes))
        assertNotNull(restarted.bitmap(id))
        assertTrue(File(directory, "$id.png").delete())
        assertFalse(restarted.contains(id))
    }
    @Test fun cacheQuotaEvictsOldManagedIcons() {
        val directory = temporary.newFolder()
        val first = png(Color.RED)
        val second = png(Color.BLUE)
        val cache = PanelAssetCache(directory, maxOf(first.size, second.size).toLong())
        assertTrue(cache.accept(PanelAssetCache.hash(first), first))
        assertTrue(cache.accept(PanelAssetCache.hash(second), second))
        assertFalse(cache.contains(PanelAssetCache.hash(first)))
        assertTrue(cache.contains(PanelAssetCache.hash(second)))
        assertTrue(directory.listFiles().orEmpty().sumOf { it.length() } <= maxOf(first.size, second.size))
    }
}

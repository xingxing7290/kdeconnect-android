/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.controlpanel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.LruCache
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow

class PanelAssetCache(private val directory: File, private val maximumBytes: Long = 128L * 1024 * 1024) {
    val changes = MutableStateFlow(0L)
    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    @Synchronized fun contains(id: String): Boolean = read(id) != null

    @Synchronized fun bitmap(id: String): Bitmap? {
        if (!validId(id)) return null
        memory.get(id)?.let { return it }
        val bytes = read(id) ?: return null
        return decode(bytes)?.also { memory.put(id, it) }
    }
    @Synchronized fun accept(id: String, bytes: ByteArray): Boolean {
        if (!validId(id) || bytes.size > MAX_BYTES || hash(bytes) != id) return false
        val bitmap = decode(bytes) ?: return false
        if (bytes.size > maximumBytes || (!directory.isDirectory && !directory.mkdirs())) return false
        val target = File(directory, "$id.png")
        val files = directory.listFiles().orEmpty().filter { it.isFile && it.extension == "png" && validId(it.nameWithoutExtension) }
        var used = files.sumOf { it.length() } - if (target.exists()) target.length() else 0
        for (file in files.sortedBy { it.lastModified() }) {
            if (used + bytes.size <= maximumBytes) break
            if (file == target) continue
            val size = file.length()
            if (file.delete()) { used -= size; memory.remove(file.nameWithoutExtension) }
        }
        if (used + bytes.size > maximumBytes) return false
        val atomic = AtomicFile(target)
        var stream: java.io.FileOutputStream? = null
        return try {
            stream = atomic.startWrite()
            stream.write(bytes)
            atomic.finishWrite(stream)
            memory.put(id, bitmap)
            changes.value++
            true
        } catch (_: java.io.IOException) {
            atomic.failWrite(stream)
            false
        }
    }
    private fun read(id: String): ByteArray? {
        if (!validId(id)) return null
        val file = File(directory, "$id.png")
        if (!file.isFile || file.length() !in 1..MAX_BYTES.toLong()) return null
        return try {
            val bytes = AtomicFile(file).openRead().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            bytes.takeIf { hash(it) == id && dimensions(it) }
        } catch (_: java.io.IOException) { null }
    }
    private fun dimensions(bytes: ByteArray): Boolean {
        if (bytes.size < 24 || !bytes.copyOfRange(0, 8).contentEquals(PNG_MAGIC)) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth == 256 && options.outHeight == 256 && options.outMimeType == "image/png"
    }
    private fun decode(bytes: ByteArray): Bitmap? = try {
        if (!dimensions(bytes)) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.takeIf { it.width == 256 && it.height == 256 }
    } catch (_: OutOfMemoryError) { null }
    companion object {
        const val MAX_BYTES = 512 * 1024
        private val ID = Regex("[a-f0-9]{64}")
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        fun validId(id: String) = ID.matches(id)
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        @Volatile private var instance: PanelAssetCache? = null
        fun get(context: Context): PanelAssetCache = instance ?: synchronized(this) {
            instance ?: PanelAssetCache(File(context.applicationContext.filesDir, "control-panel-assets")).also { instance = it }
        }
    }
}

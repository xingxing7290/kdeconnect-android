/*
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.helpers

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [23, 26, 27, 37])
class SMSVideoThumbnailTest {
    private fun retriever(frame: Bitmap?): MediaMetadataRetriever = mockk {
        if (Build.VERSION.SDK_INT >= 27) {
            every { getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 100, 100) } returns frame
        } else {
            every { getFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } returns frame
        }
    }

    @Test fun legacyFramesAreScaledAndReleasedWhileModernThumbnailsAreUnchanged() {
        val modern = Build.VERSION.SDK_INT >= 27
        val frame = Bitmap.createBitmap(if (modern) 100 else 400, if (modern) 50 else 200, Bitmap.Config.ARGB_8888)
        val retriever = retriever(frame)
        val thumbnail = requireNotNull(SMSHelper.getVideoThumbnail(retriever))
        assertEquals(100, thumbnail.width)
        assertEquals(50, thumbnail.height)
        assertFalse(thumbnail.isRecycled)
        if (modern) {
            assertSame(frame, thumbnail)
            verify(exactly = 1) { retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 100, 100) }
            verify(exactly = 0) { retriever.getFrameAtTime(any(), any()) }
        } else {
            assertTrue(frame.isRecycled)
            verify(exactly = 1) { retriever.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }
        }
        thumbnail.recycle()
    }

    @Test fun portraitAndThinFramesStayWithinBounds() {
        val modern = Build.VERSION.SDK_INT >= 27
        for (width in listOf(200, 1)) {
            val frame = Bitmap.createBitmap(if (modern) maxOf(1, width / 4) else width, if (modern) 100 else 400, Bitmap.Config.ARGB_8888)
            val thumbnail = requireNotNull(SMSHelper.getVideoThumbnail(retriever(frame)))
            assertEquals(100, thumbnail.height)
            assertEquals(maxOf(1, width / 4), thumbnail.width)
            assertFalse(thumbnail.isRecycled)
            if (!modern) assertTrue(frame.isRecycled)
            thumbnail.recycle()
        }
    }

    @Test fun sameSizedFrameIsNotRecycledBeforeTheCallerUsesIt() {
        val frame = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val thumbnail = requireNotNull(SMSHelper.getVideoThumbnail(retriever(frame)))
        assertSame(frame, thumbnail)
        assertFalse(thumbnail.isRecycled)
        thumbnail.recycle()
    }

    @Test fun missingFrameDoesNotInventAnAttachmentImage() {
        assertNull(SMSHelper.getVideoThumbnail(retriever(null)))
    }
}

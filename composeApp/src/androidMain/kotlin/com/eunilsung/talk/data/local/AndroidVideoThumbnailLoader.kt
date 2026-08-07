package com.eunilsung.talk.data.local

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.eunilsung.talk.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** MediaMetadataRetriever 로 첫 프레임·길이·해상도를 뽑는다. */
class AndroidVideoThumbnailLoader(
    private val context: Context,
) : VideoThumbnailLoader {

    override suspend fun load(path: String): VideoThumb = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext VideoThumb()
        val retriever = MediaMetadataRetriever()
        try {
            if (path.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(path))
            } else {
                retriever.setDataSource(path)
            }
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            // 90/270° 회전이면 표시상 가로세로가 바뀐다.
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val widthHeight = if (!w.isNullOrBlank() && !h.isNullOrBlank()) {
                if (rotation == 90 || rotation == 270) "$h:$w" else "$w:$h"
            } else ""

            // 첫 프레임(근처 키프레임).
            val frame: Bitmap? = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val bytes = frame?.let { bmp ->
                ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.toByteArray()
                }
            }
            VideoThumb(
                frameBytes = bytes,
                durationSec = (durationMs / 1000L).coerceAtLeast(0L),
                widthHeight = widthHeight,
            )
        } catch (e: Exception) {
            Log.message("[VideoThumb] load failed: ${e.message}")
            VideoThumb()
        } finally {
            runCatching { retriever.release() }
        }
    }
}

package com.eunilsung.talk.data.local

import platform.posix.memcpy
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImage
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.CoreMedia.CMTimeMake
import platform.AVFoundation.AVAssetImageGenerator
import kotlinx.cinterop.value
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.alloc
import com.eunilsung.talk.util.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.naturalSize
import platform.AVFoundation.tracksWithMediaType
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSURL

/** iOS — AVURLAsset 으로 첫 프레임·길이·해상도를 뽑는다. */
@OptIn(ExperimentalForeignApi::class)
class IosVideoThumbnailLoader : VideoThumbnailLoader {

    override suspend fun load(path: String): VideoThumb {
        if (path.isBlank()) return VideoThumb()
        val url = when {
            path.startsWith("file://") || path.startsWith("http") -> NSURL.URLWithString(path)
            path.startsWith("/") -> NSURL.fileURLWithPath(path)
            else -> null // PHAsset 식별자 등 URL 로 못 여는 경로.
        } ?: return VideoThumb()

        return try {
            val asset = AVURLAsset(uRL = url, options = null)
            val seconds = CMTimeGetSeconds(asset.duration)
            val durationSec = if (seconds.isNaN() || seconds < 0.0) 0L else seconds.toLong()
            val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
            val widthHeight = track?.naturalSize?.useContents {
                if (width > 0.0 && height > 0.0) "${width.toInt()}:${height.toInt()}" else ""
            } ?: ""
            VideoThumb(
                frameBytes = firstFrameJpeg(asset),
                durationSec = durationSec,
                widthHeight = widthHeight,
            )
        } catch (e: Exception) {
            Log.message("[VideoThumb] iOS load failed: ${e.message}")
            VideoThumb()
        }
    }

    /**
     * 첫 프레임 JPEG.
     *
     * `appliesPreferredTrackTransform` 을 켜지 않으면 세로로 찍은 영상의 썸네일이 눕는다
     * (Android 쪽이 rotation 을 보정하는 것과 짝을 맞춘다).
     */
    private fun firstFrameJpeg(asset: AVURLAsset): ByteArray? {
        val generator = AVAssetImageGenerator(asset).apply {
            appliesPreferredTrackTransform = true
        }
        val cgImage = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            generator.copyCGImageAtTime(
                requestedTime = CMTimeMake(value = 0, timescale = 1),
                actualTime = null,
                error = error.ptr,
            ) ?: run {
                Log.message("[VideoThumb] iOS frame extract failed: ${error.value?.localizedDescription}")
                null
            }
        } ?: return null
        val image = UIImage.imageWithCGImage(cgImage)
        return UIImageJPEGRepresentation(image, FRAME_JPEG_QUALITY)?.toByteArray()
    }

    private companion object {
        /** 목록 썸네일이라 화질보다 크기를 택한다. */
        const val FRAME_JPEG_QUALITY = 0.85
    }
}

/** [NSData] → Kotlin [ByteArray]. */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length) }
    }
}

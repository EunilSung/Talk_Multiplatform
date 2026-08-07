package com.eunilsung.talk.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

actual suspend fun compressImageToJpeg(bytes: ByteArray, maxBytes: Int): CompressedImage =
    withContext(Dispatchers.Default) {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return@withContext CompressedImage(bytes, "")

        val oriented = applyExifOrientation(bytes, decoded)

        var current = downscaleToMaxSide(oriented, 1600)
        var quality = 90
        var best = encodeJpeg(current, quality)

        repeat(12) {
            if (best.size <= maxBytes) {
                return@withContext CompressedImage(best, "${current.width}:${current.height}")
            }
            if (quality > 40) {
                quality -= 10
                best = encodeJpeg(current, quality)
            } else {
                val next = downscaleToMaxSide(current, (maxOf(current.width, current.height) * 0.8f).toInt())
                if (next.width == current.width && next.height == current.height) {
                    return@withContext CompressedImage(best, "${current.width}:${current.height}")
                }
                current = next
                quality = 80
                best = encodeJpeg(current, quality)
            }
        }
        CompressedImage(best, "${current.width}:${current.height}")
    }

/** EXIF orientation 을 읽어 회전/반전을 반영한 새 Bitmap 반환. 정상이면 원본 그대로. */
private fun applyExifOrientation(bytes: ByteArray, bmp: Bitmap): Bitmap {
    val orientation = runCatching {
        ByteArrayInputStream(bytes).use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return bmp
    }
    return runCatching {
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }.getOrDefault(bmp)
}

private fun encodeJpeg(bmp: Bitmap, quality: Int): ByteArray {
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}

private fun downscaleToMaxSide(bmp: Bitmap, maxSide: Int): Bitmap {
    val longSide = maxOf(bmp.width, bmp.height)
    if (maxSide <= 0 || longSide <= maxSide) return bmp
    val ratio = maxSide.toFloat() / longSide
    val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
    val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bmp, w, h, true)
}

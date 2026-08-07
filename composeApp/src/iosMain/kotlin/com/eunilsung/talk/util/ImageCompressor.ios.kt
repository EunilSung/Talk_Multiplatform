package com.eunilsung.talk.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual suspend fun compressImageToJpeg(bytes: ByteArray, maxBytes: Int): CompressedImage =
    withContext(Dispatchers.Default) {
        val nsData = bytes.usePinned {
            NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())
        }
        val image = UIImage.imageWithData(nsData) ?: return@withContext CompressedImage(bytes, "")

        var current = resizeToMaxSide(image, 1600.0)
        var quality = 0.9
        var best = jpegBytes(current, quality) ?: return@withContext CompressedImage(bytes, "")

        repeat(12) {
            if (best.size <= maxBytes) {
                return@withContext CompressedImage(best, sizeString(current))
            }
            if (quality > 0.4) {
                quality -= 0.1
                best = jpegBytes(current, quality) ?: best
            } else {
                val next = resizeToMaxSide(current, maxSide(current) * 0.8)
                if (maxSide(next) >= maxSide(current)) {
                    return@withContext CompressedImage(best, sizeString(current))
                }
                current = next
                quality = 0.8
                best = jpegBytes(current, quality) ?: best
            }
        }
        CompressedImage(best, sizeString(current))
    }

@OptIn(ExperimentalForeignApi::class)
private fun jpegBytes(image: UIImage, quality: Double): ByteArray? {
    val data = UIImageJPEGRepresentation(image, quality) ?: return null
    val len = data.length.toInt()
    val out = ByteArray(len)
    if (len > 0) out.usePinned { memcpy(it.addressOf(0), data.bytes, len.toULong()) }
    return out
}

@OptIn(ExperimentalForeignApi::class)
private fun maxSide(image: UIImage): Double = image.size.useContents { maxOf(width, height) }

@OptIn(ExperimentalForeignApi::class)
private fun sizeString(image: UIImage): String = image.size.useContents {
    "${(width * image.scale).toInt()}:${(height * image.scale).toInt()}"
}

@OptIn(ExperimentalForeignApi::class)
private fun resizeToMaxSide(image: UIImage, maxSide: Double): UIImage {
    val (w, h) = image.size.useContents { width to height }
    val longSide = maxOf(w, h)
    if (maxSide <= 0.0 || longSide <= maxSide) return image
    val ratio = maxSide / longSide
    val nw = w * ratio
    val nh = h * ratio
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(nw, nh), false, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, nw, nh))
    val result = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return result ?: image
}

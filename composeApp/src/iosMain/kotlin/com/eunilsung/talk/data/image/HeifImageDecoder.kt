package com.eunilsung.talk.data.image

import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.decode.SkiaImageDecoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.use
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/**
 * HEIC·HEIF 이미지를 iOS ImageIO 로 풀어 coil 에 넘기는 디코더.
 *
 * coil 은 iOS 에서 Skia 로 디코딩하는데 **Skia 는 HEIC 를 풀지 못한다.** 아이폰(설정 → 카메라 → 포맷 → 고효율성)은
 * 사진을 HEIC 로 저장하는데, 앱은 그 원본을 그대로 보여 주므로 그런 사진이 말풍선에서 배경색만 남은 회색 빈칸으로 보인다.
 * Android 는 플랫폼 디코더가 풀어 정상이다.
 *
 * ImageIO 썸네일 API 로 **요청 크기까지만 줄여 풀고, 방향도 그때 반영**해 바로 선 JPEG 로 만든 뒤
 * [SkiaImageDecoder] 에 넘긴다. 24MP 원본을 통째로 풀지 않아 말풍선이 줄지어 떠도 메모리가 크게 오르지 않는다.
 * 변환하지 못하면 원본을 그대로 넘겨 이 디코더가 없던 때와 똑같이 동작한다.
 */
class HeifImageDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val heif = source.source().use { it.readByteArray() }
        val decodable = heifToJpeg(heif, maxPixelSize(options)) ?: heif
        return SkiaImageDecoder(
            source = ImageSource(source = Buffer().apply { write(decodable) }, fileSystem = options.fileSystem),
            options = options,
        ).decode()
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (!isHeif(result.source.source())) return null
            return HeifImageDecoder(result.source, options)
        }
    }
}

/**
 * ISO BMFF 의 `ftyp` 상자 대표 브랜드로 HEIC·HEIF 를 가린다. 소스를 소비하지 않는다.
 *
 * 확장자가 아니라 내용으로 본다 — 서버 파일명은 원본 확장자를 따르지만 로컬 미리보기처럼 확장자가 없는 경로도 있다.
 */
internal fun isHeif(source: BufferedSource): Boolean =
    source.rangeEquals(FTYP_OFFSET, FTYP) && HEIF_BRANDS.any { source.rangeEquals(BRAND_OFFSET, it) }

/**
 * HEIC 를 **긴 변 [maxPixelSize] 이하로 줄이고 방향을 반영한** JPEG 로 바꾼다.
 *
 * 방향은 픽셀에 반영하고 EXIF 는 남기지 않는다 — 방향 태그를 보지 않는 PC 클라이언트에서도 바로 선다.
 *
 * @param maxPixelSize null 이면 줄이지 않는다(올릴 때 원본 해상도 유지).
 * @return 풀지 못하면 null.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun heifToJpeg(heif: ByteArray, maxPixelSize: Int?): ByteArray? =
    autoreleasepool { transcode(heif, maxPixelSize) }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun transcode(heif: ByteArray, maxPixelSize: Int?): ByteArray? {
    if (heif.isEmpty()) return null
    val data = heif.usePinned { NSData.create(bytes = it.addressOf(0), length = heif.size.toULong()) }
    val cfData = CFBridgingRetain(data)?.reinterpret<cnames.structs.__CFData>() ?: return null
    val imageSource = CGImageSourceCreateWithData(cfData, null)
    CFRelease(cfData)
    imageSource ?: return null

    val thumbnailOptions = thumbnailOptions(maxPixelSize)
    val cgImage = CGImageSourceCreateThumbnailAtIndex(imageSource, 0uL, thumbnailOptions)
    CFRelease(thumbnailOptions)
    CFRelease(imageSource)
    cgImage ?: return null

    val jpeg = UIImageJPEGRepresentation(UIImage.imageWithCGImage(cgImage), JPEG_QUALITY)
    CGImageRelease(cgImage)
    return jpeg?.toByteArray()
}

/**
 * 원본이 작아도 썸네일을 새로 만들고, EXIF 방향을 픽셀에 반영하고, 긴 변을 [maxPixelSize] 로 묶는다.
 * [maxPixelSize] 가 null 이면 크기 키를 넣지 않아 원본 크기 그대로 만든다.
 */
@OptIn(ExperimentalForeignApi::class)
private fun thumbnailOptions(maxPixelSize: Int?): CFDictionaryRef? = memScoped {
    val options = CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        3,
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    )
    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
    CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
    if (maxPixelSize != null) {
        val size = alloc<IntVar>().apply { value = maxPixelSize }
        val number = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, size.ptr)
        CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, number)
        CFRelease(number)
    }
    options
}

/**
 * coil 이 요청한 크기의 긴 변. 크기를 정하지 않은 요청(전체화면 뷰어의 원본 등)은 [MAX_PIXEL_SIZE] 로 묶는다 —
 * 24MP 를 통째로 풀면 한 장에 100MB 가까이 든다.
 */
private fun maxPixelSize(options: Options): Int {
    val requested = maxOf(options.size.width.pxOrElse { 0 }, options.size.height.pxOrElse { 0 })
    return if (requested > 0) requested.coerceAtMost(MAX_PIXEL_SIZE) else MAX_PIXEL_SIZE
}

/** [NSData] → Kotlin [ByteArray]. */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
        }
    }
}

private const val MAX_PIXEL_SIZE = 4096
private const val JPEG_QUALITY = 0.9
private const val FTYP_OFFSET = 4L
private const val BRAND_OFFSET = 8L
private val FTYP: ByteString = "ftyp".encodeUtf8()

/** HEIF 계열 대표 브랜드. 아이폰 사진은 `heic`, 연사·묶음은 `heis`·`hevc` 등, 일반 HEIF 는 `mif1`. */
private val HEIF_BRANDS: List<ByteString> =
    listOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1").map { it.encodeUtf8() }

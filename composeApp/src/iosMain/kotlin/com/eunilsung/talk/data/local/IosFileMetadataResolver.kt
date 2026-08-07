package com.eunilsung.talk.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.eunilsung.talk.util.Log
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.Photos.PHAsset
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetMediaTypeVideo
import platform.Photos.PHAssetResource
import platform.Photos.PHAssetResourceManager
import platform.Photos.PHAssetResourceRequestOptions
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsVersionCurrent
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImage
import kotlin.coroutines.resume

/** iOS [FileMetadataResolver] — 파일 경로 / PHAsset localIdentifier 분기 처리. */
@OptIn(ExperimentalForeignApi::class)
class IosFileMetadataResolver : FileMetadataResolver {

    override suspend fun writeCacheFile(filename: String, bytes: ByteArray): String? =
        withContext(Dispatchers.Default) {
            runCatching {
                val dirs = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
                val cacheDir = (dirs.firstOrNull() as? String) ?: return@withContext null
                val subDir = "$cacheDir/chat_uploads"
                NSFileManager.defaultManager.createDirectoryAtPath(subDir, true, null, null)
                val filePath = "$subDir/$filename"

                val nsData = bytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                }
                val ok = nsData.writeToFile(filePath, atomically = true)
                if (ok) filePath else null
            }.getOrNull()
        }

    override suspend fun saveToGallery(filename: String, bytes: ByteArray): String? =
        withContext(Dispatchers.Default) {
            runCatching {
                val nsData = bytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                }
                val image = UIImage.imageWithData(nsData) ?: return@withContext null
                kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                    PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                        changeBlock = {
                            PHAssetChangeRequest.creationRequestForAssetFromImage(image)
                        },
                        completionHandler = { success, _ ->
                            if (cont.isActive) {
                                cont.resume(if (success) filename else null)
                            }
                        },
                    )
                }
            }.getOrNull()
        }

    override suspend fun saveVideoToGallery(filename: String, bytes: ByteArray): String? =
        withContext(Dispatchers.Default) {
            runCatching {
                // 동영상 asset 생성은 파일 URL 이 필요 — 임시 파일로 쓴 뒤 등록.
                val nsData = bytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                }
                val tmpPath = NSTemporaryDirectory() + filename
                if (!nsData.writeToFile(tmpPath, atomically = true)) return@withContext null
                val fileUrl = NSURL.fileURLWithPath(tmpPath)

                kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                    PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                        changeBlock = {
                            PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(fileUrl)
                        },
                        completionHandler = { success, _ ->
                            runCatching { NSFileManager.defaultManager.removeItemAtPath(tmpPath, null) }
                            if (cont.isActive) {
                                cont.resume(if (success) filename else null)
                            }
                        },
                    )
                }
            }.getOrNull()
        }

    override suspend fun findDownloadedFile(filename: String): String? =
        withContext(Dispatchers.Default) {
            if (filename.isBlank()) return@withContext null
            runCatching {
                val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
                val docDir = (dirs.firstOrNull() as? String) ?: return@withContext null
                val filePath = "$docDir/$SAVE_DIR/$filename"
                if (NSFileManager.defaultManager.fileExistsAtPath(filePath)) filePath else null
            }.getOrNull()
        }

    override suspend fun saveDownloadedFile(filename: String, bytes: ByteArray): String? =
        withContext(Dispatchers.Default) {
            runCatching {
                val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
                val docDir = (dirs.firstOrNull() as? String) ?: return@withContext null
                val appDir = "$docDir/$SAVE_DIR"
                NSFileManager.defaultManager.createDirectoryAtPath(appDir, true, null, null)
                val filePath = "$appDir/$filename"

                val nsData = bytes.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                }
                val ok = nsData.writeToFile(filePath, atomically = true)
                if (ok) filePath else null
            }.getOrNull()
        }

    override suspend fun readBytes(path: String): ByteArray? = withContext(Dispatchers.Default) {
        if (path.isBlank()) return@withContext null
        return@withContext when {
            isPhAssetIdentifier(path) -> readBytesPhAsset(path.removePrefix("ph://"))
            path.startsWith("file://") || path.startsWith("/") -> readBytesFromFilePath(path)
            else -> null
        }
    }

    private fun readBytesFromFilePath(rawPath: String): ByteArray? {
        val absolute = (if (rawPath.startsWith("file://")) {
            (NSURL.URLWithString(rawPath)?.path) ?: rawPath.removePrefix("file://")
        } else rawPath).existingFilePathOrSelf()
        return runCatching {
            val data = NSFileManager.defaultManager.contentsAtPath(absolute) ?: return null
            data.toByteArray()
        }.getOrNull()
    }

    /** PHAsset 원본 bytes 읽기 (이미지/동영상 분기, [PH_READ_TIMEOUT_MS] 타임아웃). */
    private suspend fun readBytesPhAsset(localIdentifier: String): ByteArray? {
        val fetchResult = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localIdentifier), null)
        val asset = (fetchResult.firstObject() as? PHAsset) ?: return null

        val bytes = withTimeoutOrNull(PH_READ_TIMEOUT_MS) {
            if (asset.mediaType == PHAssetMediaTypeVideo) {
                readBytesAssetResource(asset)
            } else {
                readBytesImageData(asset)
            }
        }
        if (bytes == null) {
            Log.message("[IosFile] readBytesPhAsset timeout/failed id=$localIdentifier type=${asset.mediaType}")
        }
        return bytes
    }

    /** 이미지 자산 — 원본 데이터 요청. */
    private suspend fun readBytesImageData(asset: PHAsset): ByteArray? {
        val options = PHImageRequestOptions().apply {
            networkAccessAllowed = true
            synchronous = false
            deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
            version = PHImageRequestOptionsVersionCurrent
        }
        return suspendCancellableCoroutine { cont ->
            PHImageManager.defaultManager().requestImageDataAndOrientationForAsset(
                asset = asset,
                options = options,
            ) { data, _, _, _ ->
                val bytes = data?.toByteArray()
                if (cont.isActive) cont.resume(bytes)
            }
        }
    }

    /** 동영상 등 비이미지 자산 — 원본 리소스를 청크로 받아 합친다. */
    private suspend fun readBytesAssetResource(asset: PHAsset): ByteArray? {
        val resource = (PHAssetResource.assetResourcesForAsset(asset).firstOrNull() as? PHAssetResource)
            ?: return null
        val options = PHAssetResourceRequestOptions().apply { networkAccessAllowed = true }
        return suspendCancellableCoroutine { cont ->
            val chunks = mutableListOf<ByteArray>()
            PHAssetResourceManager.defaultManager().requestDataForAssetResource(
                resource = resource,
                options = options,
                dataReceivedHandler = { data -> data?.let { chunks += it.toByteArray() } },
                completionHandler = { error ->
                    if (!cont.isActive) return@requestDataForAssetResource
                    if (error != null) {
                        cont.resume(null)
                    } else {
                        val total = chunks.sumOf { it.size }
                        val out = ByteArray(total)
                        var offset = 0
                        chunks.forEach { chunk ->
                            chunk.copyInto(out, offset)
                            offset += chunk.size
                        }
                        cont.resume(out)
                    }
                },
            )
        }
    }

    /** NSData → ByteArray 변환 (cinterop pinning). */
    private fun NSData.toByteArray(): ByteArray {
        val size = this.length.toInt()
        if (size == 0) return ByteArray(0)
        val out = ByteArray(size)
        out.usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
        return out
    }

    override suspend fun resolve(path: String): FileMetadata = withContext(Dispatchers.Default) {
        if (path.isBlank()) return@withContext FileMetadata("", "", "", -1L)

        return@withContext when {
            isPhAssetIdentifier(path) -> resolvePhAsset(path.removePrefix("ph://"))
            path.startsWith("file://") || path.startsWith("/") -> resolveFilePath(path)
            else -> FileMetadata("", "", "", -1L)
        }
    }

    /** PHAsset 식별자는 이미지 로더가 열 수 없어 캐시 파일로 복사한다. */
    override suspend fun materializeForDisplay(path: String, filename: String): String {
        if (!isPhAssetIdentifier(path)) return path
        val bytes = readBytes(path)
        if (bytes == null || bytes.isEmpty()) {
            Log.message("[FileMeta] materialize failed — read empty: $path")
            return path
        }
        return writeCacheFile(filename, bytes) ?: path.also {
            Log.message("[FileMeta] materialize failed — cache write: $path")
        }
    }

    /** PHAsset localIdentifier 패턴 감지 — `ph://` prefix 또는 `{UUID}/L0/{seq}` 형식. */
    private fun isPhAssetIdentifier(s: String): Boolean {
        if (s.startsWith("ph://")) return true
        if (s.startsWith("/") || s.startsWith("file://")) return false
        return "/L" in s && s.length > 20
    }

    private fun resolveFilePath(rawPath: String): FileMetadata {
        val absolute = (if (rawPath.startsWith("file://")) {
            (NSURL.URLWithString(rawPath)?.path) ?: rawPath.removePrefix("file://")
        } else rawPath).existingFilePathOrSelf()

        val name = absolute.substringAfterLast('/').toNfc()
        val extension = extractExtension(name)

        val size = runCatching {
            val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(absolute, null)
            (attrs?.get(NSFileSize) as? NSNumber)?.longValue ?: -1L
        }.getOrDefault(-1L)

        val widthHeight = if (isImageExtension(extension)) {
            runCatching {
                val img = UIImage.imageWithContentsOfFile(absolute)
                img?.size?.useContents {
                    val w = width.toInt()
                    val h = height.toInt()
                    if (w > 0 && h > 0) "$w:$h" else null
                } ?: ""
            }.getOrDefault("")
        } else ""

        return FileMetadata(
            originalName = name,
            extension = extension,
            widthHeight = widthHeight,
            sizeBytes = size,
        )
    }

    private fun resolvePhAsset(localIdentifier: String): FileMetadata {
        return runCatching {
            val identifiers = listOf(localIdentifier)
            val fetchResult = PHAsset.fetchAssetsWithLocalIdentifiers(identifiers, null)
            val asset = (fetchResult.firstObject() as? PHAsset)
                ?: return FileMetadata("", "", "", -1L)

            val w = asset.pixelWidth.toInt()
            val h = asset.pixelHeight.toInt()
            val isImage = asset.mediaType == PHAssetMediaTypeImage
            val isVideo = asset.mediaType == PHAssetMediaTypeVideo
            val widthHeight = if ((isImage || isVideo) && w > 0 && h > 0) "$w:$h" else ""

            val resources = PHAssetResource.assetResourcesForAsset(asset)
            val firstResource = resources.firstOrNull() as? PHAssetResource
            val originalName = firstResource?.originalFilename.orEmpty().toNfc()
            val extension = extractExtension(originalName)

            val sizeBytes: Long = -1L

            FileMetadata(
                originalName = originalName,
                extension = extension,
                widthHeight = widthHeight,
                sizeBytes = sizeBytes,
            )
        }.getOrElse { FileMetadata("", "", "", -1L) }
    }

    private fun extractExtension(filename: String): String {
        val idx = filename.lastIndexOf('.')
        return if (idx > 0 && idx < filename.length - 1) filename.substring(idx) else ""
    }

    private fun isImageExtension(ext: String): Boolean =
        ext.lowercase() in IMAGE_EXTENSIONS

    private companion object {
        /** PHAsset 원본 읽기 최대 대기(ms). */
        const val PH_READ_TIMEOUT_MS = 60_000L

        val IMAGE_EXTENSIONS = setOf(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".heic", ".heif"
        )
    }
}

/** 사용자에게 보이는 저장 폴더명 — 갤러리·다운로드 하위에 이 이름으로 모은다. */
private const val SAVE_DIR = "MultiplatformTalk"

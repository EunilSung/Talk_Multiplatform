package com.eunilsung.talk.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cValue
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.eunilsung.talk.util.Log
import platform.CoreGraphics.CGSize
import platform.Foundation.NSData
import platform.Foundation.NSPredicate
import platform.Foundation.NSSortDescriptor
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Photos.PHAsset
import platform.Photos.PHAssetCollection
import platform.Photos.PHAssetCollectionSubtypeAny
import platform.Photos.PHAssetCollectionSubtypeSmartAlbumAllHidden
import platform.Photos.PHAssetCollectionSubtypeSmartAlbumUserLibrary
import platform.Photos.PHAssetCollectionTypeAlbum
import platform.Photos.PHAssetCollectionTypeSmartAlbum
import platform.Photos.PHFetchResult
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetMediaTypeVideo
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHFetchOptions
import platform.Photos.PHImageContentModeAspectFill
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeFastFormat
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.coroutines.resume

/**
 * iOS [RecentPhotosProvider] — Photos framework.
 *
 * @param thumbnailSizePx 썸네일 한 변 크기 (PHImageManager 의 targetSize).
 */
@OptIn(ExperimentalForeignApi::class)
class IosRecentPhotosProvider(
    private val thumbnailSizePx: Double = 480.0,
) : RecentPhotosProvider {

    override suspend fun fetchRecent(
        limit: Int,
        onPartial: (RecentPhotosResult) -> Unit,
    ): RecentPhotosResult = fetchByAlbum(albumId = null, limit = limit, onPartial = onPartial)

    override suspend fun fetchByAlbum(
        albumId: String?,
        limit: Int,
        onPartial: (RecentPhotosResult) -> Unit,
    ): RecentPhotosResult = withContext(Dispatchers.Default) {
        val status = PHPhotoLibrary.authorizationStatus()
        val granted = status == PHAuthorizationStatusAuthorized
        val limited = status == PHAuthorizationStatusLimited

        if (!granted && !limited) {
            Log.message("[RecentPhotos] iOS access denied (status=$status)")
            return@withContext RecentPhotosResult.Denied
        }

        fun wrap(list: List<RecentPhoto>): RecentPhotosResult =
            if (limited) RecentPhotosResult.Limited(list) else RecentPhotosResult.Granted(list)

        val photos = runCatching {
            val options = PHFetchOptions().apply {
                sortDescriptors = listOf(
                    NSSortDescriptor.sortDescriptorWithKey("creationDate", ascending = false)
                )
                fetchLimit = limit.toULong()
            }
            val requestedAlbum = albumId?.takeIf { it.isNotBlank() }
            // 앨범이 지정되면 그 컬렉션 안에서만 조회한다.
            val collection = requestedAlbum?.let { id ->
                PHAssetCollection
                    .fetchAssetCollectionsWithLocalIdentifiers(listOf(id), null)
                    .firstObject as? PHAssetCollection
            }
            val result = when {
                collection != null -> PHAsset.fetchAssetsInAssetCollection(collection, options)
                // 앨범을 지정했는데 못 찾은 경우(삭제됐거나 식별자가 낡음) 전체로 되돌리면
                // "비디오" 를 골랐는데 전체 사진이 뜨는 꼴이 된다. 빈 목록이 덜 혼란스럽다.
                requestedAlbum != null -> {
                    Log.message("[RecentPhotos] iOS album not found: $requestedAlbum")
                    return@runCatching emptyList()
                }
                else -> PHAsset.fetchAssetsWithOptions(options)
            }
            val count = result.count.toInt()

            val assets = (0 until count).mapNotNull {
                result.objectAtIndex(it.toULong()) as? PHAsset
            }.filter {
                it.mediaType == PHAssetMediaTypeImage || it.mediaType == PHAssetMediaTypeVideo
            }
            val n = assets.size
            val slots = ArrayList(
                assets.map {
                    RecentPhoto(
                        id = it.localIdentifier,
                        uri = it.localIdentifier,
                        thumbnailBytes = null,
                        isVideo = it.mediaType == PHAssetMediaTypeVideo,
                        durationSec = it.duration.toLong(),
                    )
                }
            )
            if (n > 0) onPartial(wrap(slots.toList()))

            val mutex = Mutex()
            var completed = 0
            coroutineScope {
                assets.mapIndexed { i, asset ->
                    async {
                        val bytes = loadThumbnail(asset, thumbnailSizePx)
                        mutex.withLock {
                            slots[i] = slots[i].copy(thumbnailBytes = bytes)
                            completed++
                            if (completed % 12 == 0 || completed == n) {
                                onPartial(wrap(slots.toList()))
                            }
                        }
                    }
                }.awaitAll()
            }
            slots.toList()
        }.getOrElse {
            Log.message("[RecentPhotos] iOS fetch failed: ${it.message}")
            emptyList()
        }

        wrap(photos)
    }

    /**
     * 스마트 앨범(즐겨찾기·스크린샷·동영상 등) + 사용자 앨범 목록.
     *
     * 조회 시 subtype 은 반드시 [PHAssetCollectionSubtypeAny] 를 쓴다. `AlbumRegular`(=2) 는
     * Album 타입 전용 subtype 이라 SmartAlbum 타입에 넘기면 결과가 0건이 되어 목록이 통째로 빈다.
     *
     * 제외 대상: "최근 항목"(UserLibrary — 맨 위 "전체" 와 중복), "숨겨진 항목"(AllHidden).
     * 비어 있는 앨범도 제외한다. iOS 는 한 사진이 여러 앨범에 속할 수 있어 개수 합이 전체와
     * 일치하지 않는데, 이는 사진 앱과 같은 동작이라 그대로 둔다.
     */
    override suspend fun fetchAlbums(): List<PhotoAlbum> = withContext(Dispatchers.Default) {
        if (!hasPhotoAccess()) return@withContext emptyList()

        runCatching {
            val albums = mutableListOf<Pair<PhotoAlbum, PHAsset?>>()

            fun collect(result: PHFetchResult) {
                val total = result.count.toInt()
                for (i in 0 until total) {
                    val collection = result.objectAtIndex(i.toULong()) as? PHAssetCollection
                        ?: continue
                    val subtype = collection.assetCollectionSubtype
                    if (subtype == PHAssetCollectionSubtypeSmartAlbumUserLibrary) continue
                    if (subtype == PHAssetCollectionSubtypeSmartAlbumAllHidden) continue

                    val assets = PHAsset.fetchAssetsInAssetCollection(collection, mediaFetchOptions())
                    val count = assets.count.toInt()
                    if (count == 0) continue
                    albums += PhotoAlbum(
                        id = collection.localIdentifier,
                        name = collection.localizedTitle ?: "",
                        count = count,
                    ) to (assets.objectAtIndex(0u) as? PHAsset)
                }
            }

            collect(
                PHAssetCollection.fetchAssetCollectionsWithType(
                    PHAssetCollectionTypeSmartAlbum,
                    PHAssetCollectionSubtypeAny,
                    null,
                )
            )
            collect(
                PHAssetCollection.fetchAssetCollectionsWithType(
                    PHAssetCollectionTypeAlbum,
                    PHAssetCollectionSubtypeAny,
                    null,
                )
            )

            val sorted = albums
                .filter { it.first.name.isNotBlank() }
                .sortedByDescending { it.first.count }

            // 대표 이미지는 URI 로 못 넘겨 썸네일 바이트로 싣는다 — 행 높이(48dp)에 맞춰 작게.
            // 없어도 목록은 쓸 수 있으므로 iCloud 다운로드를 막고, 늦으면 타임아웃 후
            // 이름·개수만 반환한다. 이미지 한 장 때문에 목록 전체를 볼모로 잡지 않는다.
            withTimeoutOrNull(ALBUM_COVER_TIMEOUT_MS) {
                coroutineScope {
                    sorted.map { (album, cover) ->
                        async {
                            val bytes = cover?.let {
                                loadThumbnail(it, ALBUM_COVER_SIZE_PX, allowNetwork = false)
                            }
                            album.copy(coverBytes = bytes)
                        }
                    }.awaitAll()
                }
            } ?: sorted.map { it.first }
        }.getOrElse {
            Log.message("[RecentPhotos] iOS album fetch failed: ${it.message}")
            emptyList()
        }
    }

    /**
     * 사진·동영상만 남기고 최신순으로 정렬하는 fetch 옵션.
     *
     * 옵션 없이 조회하면 그리드에 못 그리는 항목까지 세어 개수가 실제와 어긋나고, 정렬이 없으면
     * 앨범 순서(=대체로 오래된 것부터)라 대표 이미지가 가장 오래된 사진이 된다.
     * (PHAssetMediaType: image=1, video=2)
     */
    private fun mediaFetchOptions(): PHFetchOptions = PHFetchOptions().apply {
        predicate = NSPredicate.predicateWithFormat("mediaType == 1 OR mediaType == 2")
        sortDescriptors = listOf(
            NSSortDescriptor.sortDescriptorWithKey("creationDate", ascending = false)
        )
    }

    override fun hasPhotoAccess(): Boolean {
        val status = PHPhotoLibrary.authorizationStatus()
        return status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
    }

    override fun requestPhotoAccess() {
        val status = PHPhotoLibrary.authorizationStatus()
        when (status) {
            PHAuthorizationStatusNotDetermined -> {
                PHPhotoLibrary.requestAuthorization { _ ->
                }
            }
            else -> {
                openAppSettings()
            }
        }
    }

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        val app = UIApplication.sharedApplication
        if (app.canOpenURL(url)) {
            app.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
    }

    /** PHAsset → JPEG ByteArray. */
    private suspend fun loadThumbnail(
        asset: PHAsset,
        sizePx: Double,
        allowNetwork: Boolean = true,
    ): ByteArray? =
        suspendCancellableCoroutine { cont ->
            val opts = PHImageRequestOptions().apply {
                setDeliveryMode(
                    if (allowNetwork) PHImageRequestOptionsDeliveryModeHighQualityFormat
                    else PHImageRequestOptionsDeliveryModeFastFormat
                )
                setNetworkAccessAllowed(allowNetwork)
                setSynchronous(false)
            }
            val targetSize = cValue<CGSize> {
                width = sizePx
                height = sizePx
            }
            val requestId = PHImageManager.defaultManager().requestImageForAsset(
                asset = asset,
                targetSize = targetSize,
                contentMode = PHImageContentModeAspectFill,
                options = opts
            ) { image: UIImage?, _ ->
                if (!cont.isActive) return@requestImageForAsset
                if (image == null) {
                    cont.resume(null)
                    return@requestImageForAsset
                }
                val jpegData = UIImageJPEGRepresentation(image, 0.9)
                cont.resume(jpegData?.toByteArray())
            }
            cont.invokeOnCancellation {
                PHImageManager.defaultManager().cancelImageRequest(requestId)
            }
        }

    private companion object {
        /** 앨범 목록 행의 대표 이미지 크기(px). */
        const val ALBUM_COVER_SIZE_PX = 200.0

        /** 대표 이미지 로딩 상한 — 넘기면 이름·개수만 먼저 보여준다. */
        const val ALBUM_COVER_TIMEOUT_MS = 2_000L
    }
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

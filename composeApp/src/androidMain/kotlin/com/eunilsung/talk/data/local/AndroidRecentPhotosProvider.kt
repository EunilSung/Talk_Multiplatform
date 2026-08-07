package com.eunilsung.talk.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.eunilsung.talk.util.Log
import java.io.ByteArrayOutputStream

/** Android [RecentPhotosProvider] — MediaStore 기반. */
class AndroidRecentPhotosProvider(
    private val context: Context,
) : RecentPhotosProvider {

    override suspend fun fetchRecent(
        limit: Int,
        onPartial: (RecentPhotosResult) -> Unit,
    ): RecentPhotosResult = fetchByAlbum(albumId = null, limit = limit, onPartial = onPartial)

    override suspend fun fetchByAlbum(
        albumId: String?,
        limit: Int,
        onPartial: (RecentPhotosResult) -> Unit,
    ): RecentPhotosResult = withContext(Dispatchers.IO) {
        if (!hasFullMediaAccess()) {
            Log.message("[RecentPhotos] permission denied — return Denied")
            return@withContext RecentPhotosResult.Denied
        }

        val isQ = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val filesUri = if (isQ) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Files.getContentUri("external")
        }
        val imageBase = if (isQ) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION") MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val videoBase = if (isQ) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION") MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = if (isQ) {
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DURATION,
            )
        } else {
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
            )
        }
        // 미디어 타입 조건은 괄호로 묶어야 앨범 조건이 OR 뒤쪽에만 걸리지 않는다.
        val typeCondition = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
        val typeArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val selection = if (albumId.isNullOrBlank()) {
            typeCondition
        } else {
            "$typeCondition AND ${MediaStore.Files.FileColumns.BUCKET_ID} = ?"
        }
        val selectionArgs = if (albumId.isNullOrBlank()) typeArgs else typeArgs + albumId
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val items = runCatching {
            val results = mutableListOf<RecentPhoto>()
            context.contentResolver.query(filesUri, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val durCol = if (isQ) cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION) else -1
                    while (cursor.moveToNext() && results.size < limit) {
                        val id = cursor.getLong(idCol)
                        val isVideo = cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        val base = if (isVideo) videoBase else imageBase
                        val uri = ContentUris.withAppendedId(base, id).toString()
                        val durationSec = if (isVideo && durCol >= 0) {
                            (cursor.getLong(durCol) / 1000L).coerceAtLeast(0L)
                        } else 0L
                        results += RecentPhoto(
                            id = id.toString(),
                            uri = uri,
                            isVideo = isVideo,
                            durationSec = durationSec,
                        )
                    }
                }
            results
        }.getOrElse {
            Log.message("[RecentPhotos] query failed: ${it.message}")
            return@withContext RecentPhotosResult.Granted(emptyList())
        }

        // 동영상 썸네일은 loadThumbnail 로 점진적으로 디코드해 채운다.
        onPartial(RecentPhotosResult.Granted(items))

        if (!isQ || items.none { it.isVideo }) {
            return@withContext RecentPhotosResult.Granted(items)
        }

        val slots = ArrayList(items)
        var decoded = 0
        slots.forEachIndexed { i, item ->
            if (!item.isVideo) return@forEachIndexed
            val bytes = runCatching {
                val bmp = context.contentResolver.loadThumbnail(
                    Uri.parse(item.uri),
                    Size(480, 480),
                    null,
                )
                ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.toByteArray()
                }
            }.getOrNull()
            if (bytes != null) {
                slots[i] = item.copy(thumbnailBytes = bytes)
                decoded++
                if (decoded % 8 == 0) onPartial(RecentPhotosResult.Granted(slots.toList()))
            }
        }
        RecentPhotosResult.Granted(slots.toList())
    }

    /**
     * BUCKET(실제 폴더) 단위 앨범 목록.
     *
     * MediaStore 는 GROUP BY 를 공개 API 로 지원하지 않아, 최신순으로 훑으며 버킷별로 접는다.
     * 첫 등장 항목이 그 폴더의 최신 사진이므로 그대로 대표 이미지로 쓴다.
     * 정렬은 "최근에 찍은 폴더가 위" — 카메라·스크린샷처럼 자주 쓰는 폴더가 자연스럽게 올라온다.
     */
    override suspend fun fetchAlbums(): List<PhotoAlbum> = withContext(Dispatchers.IO) {
        if (!hasFullMediaAccess()) return@withContext emptyList()

        val isQ = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val filesUri = if (isQ) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION") MediaStore.Files.getContentUri("external")
        }
        val imageBase = if (isQ) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION") MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val videoBase = if (isQ) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION") MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
        )
        val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        runCatching {
            val ordered = LinkedHashMap<String, PhotoAlbum>()
            context.contentResolver.query(filesUri, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val bucketIdCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
                    val bucketNameCol =
                        cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                    if (bucketIdCol < 0) return@use

                    while (cursor.moveToNext()) {
                        val bucketId = cursor.getString(bucketIdCol) ?: continue
                        val existing = ordered[bucketId]
                        if (existing != null) {
                            ordered[bucketId] = existing.copy(count = existing.count + 1)
                            continue
                        }
                        val isVideo = cursor.getInt(typeCol) ==
                                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        val base = if (isVideo) videoBase else imageBase
                        val cover = ContentUris
                            .withAppendedId(base, cursor.getLong(idCol))
                            .toString()
                        val name = bucketNameCol
                            .takeIf { it >= 0 }
                            ?.let { cursor.getString(it) }
                            ?.takeIf { it.isNotBlank() }
                            ?: bucketId
                        ordered[bucketId] = PhotoAlbum(
                            id = bucketId,
                            name = name,
                            count = 1,
                            coverUri = cover,
                        )
                    }
                }
            ordered.values.toList()
        }.getOrElse {
            Log.message("[RecentPhotos] album query failed: ${it.message}")
            emptyList()
        }
    }

    override fun hasPhotoAccess(): Boolean = hasFullMediaAccess()

    override fun requestPhotoAccess() {
        if (hasFullMediaAccess()) return

        val launcher = AndroidActivityHolder.photoPermissionLauncher
        if (launcher == null) {
            Log.message("[PhotoPermission] launcher not registered — fallback to settings")
            openAppSettings()
            return
        }

        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        runCatching { launcher.launch(perms) }
            .onFailure { Log.message("[PhotoPermission] request launch failed: ${it.message}") }
    }

    override fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.message("[RecentPhotos] openAppSettings failed: ${it.message}") }
    }

    private fun hasReadImagesPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /** 이미지·동영상 권한이 모두 있어야 true (API 32- 는 READ_EXTERNAL_STORAGE 하나로 커버). */
    private fun hasFullMediaAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        val images = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        val videos = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
        return images && videos
    }
}

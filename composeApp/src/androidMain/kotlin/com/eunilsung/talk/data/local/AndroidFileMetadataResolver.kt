package com.eunilsung.talk.data.local

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Android [FileMetadataResolver] — content URI / 파일 경로 분기 처리. */
class AndroidFileMetadataResolver(
    private val context: Context,
) : FileMetadataResolver {

    override suspend fun writeCacheFile(filename: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "chat_uploads").apply { if (!exists()) mkdirs() }
                val outFile = File(dir, filename)
                outFile.writeBytes(bytes)
                outFile.absolutePath
            }.getOrNull()
        }

    override suspend fun saveToGallery(filename: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                runCatching {
                    val resolver = context.contentResolver
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(
                            android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                            android.os.Environment.DIRECTORY_PICTURES + "/$SAVE_DIR"
                        )
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values,
                    ) ?: return@runCatching null
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: return@runCatching null
                    val finalValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, finalValues, null, null)
                    uri.toString()
                }.getOrElse { null }?.let { return@withContext it }
            }

            runCatching {
                @Suppress("DEPRECATION")
                val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_PICTURES
                )
                val appDir = File(picturesDir, SAVE_DIR).apply { if (!exists()) mkdirs() }
                val outFile = File(appDir, filename)
                outFile.writeBytes(bytes)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf("image/*"),
                    null,
                )
                outFile.absolutePath
            }.getOrNull()
        }

    override suspend fun saveVideoToGallery(filename: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                runCatching {
                    val resolver = context.contentResolver
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(
                            android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                            android.os.Environment.DIRECTORY_MOVIES + "/$SAVE_DIR"
                        )
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        values,
                    ) ?: return@runCatching null
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: return@runCatching null
                    val finalValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, finalValues, null, null)
                    uri.toString()
                }.getOrElse { null }?.let { return@withContext it }
            }

            runCatching {
                @Suppress("DEPRECATION")
                val moviesDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MOVIES
                )
                val appDir = File(moviesDir, SAVE_DIR).apply { if (!exists()) mkdirs() }
                val outFile = File(appDir, filename)
                outFile.writeBytes(bytes)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf("video/*"),
                    null,
                )
                outFile.absolutePath
            }.getOrNull()
        }

    override suspend fun findDownloadedFile(filename: String): String? =
        withContext(Dispatchers.IO) {
            if (filename.isBlank()) return@withContext null

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                runCatching {
                    val resolver = context.contentResolver
                    val projection = arrayOf(
                        android.provider.MediaStore.MediaColumns._ID,
                    )
                    val selection =
                        "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                                "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ?"
                    val relativePath = android.os.Environment.DIRECTORY_DOWNLOADS + "/$SAVE_DIR/"
                    val selectionArgs = arrayOf(filename, relativePath)
                    resolver.query(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        projection, selection, selectionArgs, null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(
                                cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                            )
                            android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                            ).toString()
                        } else null
                    }
                }.getOrNull()?.let { return@withContext it }
            }

            val candidates = listOfNotNull(
                @Suppress("DEPRECATION")
                File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ),
                    "$SAVE_DIR/$filename",
                ),
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    ?.let { File(it, "$SAVE_DIR/$filename") },
            )
            candidates.firstOrNull { it.exists() }?.absolutePath
        }

    override suspend fun saveDownloadedFile(filename: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                runCatching {
                    val resolver = context.contentResolver
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(
                            android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                            android.os.Environment.DIRECTORY_DOWNLOADS + "/$SAVE_DIR"
                        )
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values,
                    ) ?: return@runCatching null
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: return@runCatching null
                    val finalValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, finalValues, null, null)
                    uri.toString()
                }.getOrElse { null }?.let { return@withContext it }
            }

            runCatching {
                @Suppress("DEPRECATION")
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val appDir = File(downloadsDir, SAVE_DIR).apply { if (!exists()) mkdirs() }
                val outFile = File(appDir, filename)
                outFile.writeBytes(bytes)
                outFile.absolutePath
            }.getOrElse { null }?.let { return@withContext it }

            runCatching {
                val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    ?: return@runCatching null
                val appDir = File(base, SAVE_DIR).apply { if (!exists()) mkdirs() }
                val outFile = File(appDir, filename)
                outFile.writeBytes(bytes)
                outFile.absolutePath
            }.getOrNull()
        }

    override suspend fun readBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext null
        val uri = runCatching { Uri.parse(path) }.getOrNull()
        val scheme = uri?.scheme
        runCatching {
            when {
                scheme == "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                scheme == "file" || scheme == null -> File(uri?.path ?: path).readBytes()
                else -> null
            }
        }.getOrNull()
    }

    override suspend fun resolve(path: String): FileMetadata = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext FileMetadata("", "", "", -1L)

        val uri = runCatching { Uri.parse(path) }.getOrNull()
        val scheme = uri?.scheme

        return@withContext when {
            scheme == "content" -> resolveContentUri(uri!!)
            scheme == "file" || scheme == null -> resolveFilePath(uri?.path ?: path)
            else -> FileMetadata("", "", "", -1L)
        }
    }

    private fun resolveContentUri(uri: Uri): FileMetadata {
        val resolver = context.contentResolver
        var name = ""
        var size = -1L

        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx).orEmpty()
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        }

        val extension = extractExtension(name)
        val isImage = isImageExtension(extension)
                || resolver.getType(uri)?.startsWith("image/") == true
        val widthHeight = if (isImage) decodeBoundsFromUri(resolver, uri) else ""

        return FileMetadata(
            originalName = name,
            extension = extension,
            widthHeight = widthHeight,
            sizeBytes = size,
        )
    }

    private fun resolveFilePath(absolutePath: String): FileMetadata {
        val file = File(absolutePath)
        val name = file.name
        val extension = extractExtension(name)
        val size = runCatching { file.length() }.getOrDefault(-1L).takeIf { it > 0 } ?: -1L
        val widthHeight = if (isImageExtension(extension)) decodeBoundsFromFile(file) else ""

        return FileMetadata(
            originalName = name,
            extension = extension,
            widthHeight = widthHeight,
            sizeBytes = size,
        )
    }

    private fun extractExtension(filename: String): String {
        val idx = filename.lastIndexOf('.')
        return if (idx > 0 && idx < filename.length - 1) filename.substring(idx) else ""
    }

    private fun isImageExtension(ext: String): Boolean =
        ext.lowercase() in IMAGE_EXTENSIONS

    private fun decodeBoundsFromUri(resolver: ContentResolver, uri: Uri): String = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
        if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth}:${opts.outHeight}" else ""
    }.getOrDefault("")

    private fun decodeBoundsFromFile(file: File): String = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth}:${opts.outHeight}" else ""
    }.getOrDefault("")

    private companion object {
        val IMAGE_EXTENSIONS = setOf(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".heic", ".heif"
        )
    }
}

/** 사용자에게 보이는 저장 폴더명 — 갤러리·다운로드 하위에 이 이름으로 모은다. */
private const val SAVE_DIR = "MultiplatformTalk"

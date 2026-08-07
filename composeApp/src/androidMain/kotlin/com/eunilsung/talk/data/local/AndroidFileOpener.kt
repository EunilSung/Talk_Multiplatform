package com.eunilsung.talk.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android [FileOpener] — content URI 또는 file path 를 `Intent.ACTION_VIEW` 로 띄움. */
class AndroidFileOpener(
    private val context: Context,
) : FileOpener {
    override suspend fun open(path: String): Boolean = withContext(Dispatchers.Default) {
        if (path.isBlank()) return@withContext false
        runCatching {
            val uri: Uri = if (path.startsWith("content://")) {
                Uri.parse(path)
            } else {
                Uri.parse("file://$path")
            }
            val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse { false }
    }
}

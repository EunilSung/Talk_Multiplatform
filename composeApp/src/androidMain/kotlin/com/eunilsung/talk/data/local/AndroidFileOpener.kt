package com.eunilsung.talk.data.local

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.file_open_chooser
import org.jetbrains.compose.resources.getString
import java.io.File

/**
 * Android [FileOpener] — 첨부를 연결 프로그램 선택기(`Intent.createChooser`)로 넘긴다.
 *
 * 경로는 두 가지로 들어온다.
 * - `content://...` — 그대로 쓰고 MIME 은 ContentResolver 에서 얻는다.
 * - 절대 경로(앱 cache 의 열기 전용 사본) — API 24+ 에서 `file://` 를 다른 앱에 넘기면 FileUriExposedException
 *   이므로 FileProvider 로 content:// 를 만든다. FileProvider 는 그 사본 폴더만 내준다(`res/xml/file_paths.xml`).
 *
 * 읽기 권한은 인텐트의 [Intent.FLAG_GRANT_READ_URI_PERMISSION] 과 ClipData 로만 준다. 시스템이 사용자가 고른 앱에만
 * 그 화면이 떠 있는 동안 임시 권한을 준다. 선택기에 뜬 앱마다 미리 [Context.grantUriPermission] 하지 않는다 —
 * 고르지 않은 앱에도 권한이 남고 회수되지 않기 때문이다.
 */
class AndroidFileOpener(
    private val context: Context,
) : FileOpener {
    override suspend fun open(path: String): Boolean = withContext(Dispatchers.Default) {
        if (path.isBlank()) return@withContext false

        val uri = resolveUri(path) ?: return@withContext false
        val chooserTitle = getString(Res.string.file_open_chooser)

        val candidates = buildList {
            add(resolveMimeType(uri, path))
            if (!contains(FALLBACK_MIME)) add(FALLBACK_MIME)
        }
        candidates.forEach { mime ->
            if (launchChooser(uri, mime, chooserTitle)) return@withContext true
        }
        false
    }

    /** 해당 MIME 을 처리할 앱이 있으면 선택기를 띄우고 true. 없으면 false 로 다음 후보에 넘긴다. */
    private fun launchChooser(uri: Uri, mime: String, chooserTitle: String): Boolean {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val hasTarget = runCatching {
            context.packageManager.queryIntentActivities(viewIntent, 0).isNotEmpty()
        }.getOrDefault(false)
        if (!hasTarget) return false

        val chooser = Intent.createChooser(viewIntent, chooserTitle).apply {
            clipData = viewIntent.clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun resolveUri(path: String): Uri? {
        if (path.startsWith("content://")) return runCatching { Uri.parse(path) }.getOrNull()

        val rawPath = if (path.startsWith("file://")) Uri.parse(path).path ?: return null else path
        val file = File(rawPath)
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    /**
     * 확장자 기반 MIME. 한글이나 공백이 섞인 파일명은 `MimeTypeMap.getFileExtensionFromUrl` 이
     * 실패하므로 문자열에서 직접 잘라 쓴다. 못 찾으면 전체 앱 대상 fallback MIME 을 쓴다.
     */
    private fun resolveMimeType(uri: Uri, path: String): String {
        if (uri.scheme == "content") {
            runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return FALLBACK_MIME
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: FALLBACK_MIME
    }

    private companion object {
        const val FALLBACK_MIME = "*/*"
    }
}

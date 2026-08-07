package com.eunilsung.talk.data.local

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.eunilsung.talk.util.Log

/**
 * Android 외부 URL 열기.
 *  - `.apk` URL : [DownloadManager] 다운로드 후 설치 Intent 발화
 *  - 그 외 URL : `Intent.ACTION_VIEW` 로 위임
 */
class AndroidExternalUrlOpener(
    private val context: Context,
    private val appExiter: AppExiter,
) : ExternalUrlOpener {

    private val apkFileName = "TalkUpdate.apk"

    private val _isDownloading = MutableStateFlow(false)
    override val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    override fun open(url: String) {
        if (url.isBlank()) return
        if (url.endsWith(".apk", ignoreCase = true)) {
            downloadAndInstallApk(url)
        } else {
            openWithViewIntent(url)
        }
    }

    /** 일반 URL — 기본 브라우저 / Play Store 앱에 위임. */
    private fun openWithViewIntent(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { Log.message("[UrlOpener/Android] failed url=$url: ${it.message}") }
    }

    /** APK 다운로드 후 완료 broadcast 수신 시 설치 Intent 를 발화한다. */
    private fun downloadAndInstallApk(url: String) {
        _isDownloading.value = true
        runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            targetDir?.let { dir ->
                val existing = java.io.File(dir, apkFileName)
                if (existing.exists()) existing.delete()
            }

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Talk 업데이트")
                .setDescription("최신 버전 다운로드 중...")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    apkFileName
                )
            val downloadId = dm.enqueue(request)
            Log.message("[UrlOpener/Android] APK download enqueued id=$downloadId url=$url")

            registerCompleteReceiver(downloadId)
        }.onFailure {
            Log.message("[UrlOpener/Android] APK download failed: ${it.message}")
            _isDownloading.value = false
        }
    }

    private fun registerCompleteReceiver(downloadId: Long) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return

                runCatching {
                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val uri = dm.getUriForDownloadedFile(downloadId)
                    if (uri == null) {
                        Log.message("[UrlOpener/Android] download URI null — install skipped")
                    } else {
                        Log.message("[UrlOpener/Android] APK downloaded → install URI=$uri")
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(installIntent)
                    }
                }.onFailure {
                    Log.message("[UrlOpener/Android] install Intent failed: ${it.message}")
                }

                runCatching { ctx.unregisterReceiver(this) }
                _isDownloading.value = false
                appExiter.exit()
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }
}

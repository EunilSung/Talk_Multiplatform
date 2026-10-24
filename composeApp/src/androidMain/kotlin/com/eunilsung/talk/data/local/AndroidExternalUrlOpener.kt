package com.eunilsung.talk.data.local

import com.eunilsung.talk.R
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
 *  - [open] : 언제나 `Intent.ACTION_VIEW` + `CATEGORY_BROWSABLE` 로 브라우저에 넘긴다. 주소가 `.apk` 로 끝나도 받지 않는다.
 *  - [openUpdate] : 강제 업데이트 안내만 쓴다. https 이고 경로가 `.apk` 로 끝나면 [DownloadManager] 로 받아 설치 Intent 를 띄운다.
 *    `market://details?id=` 는 Play 스토어로, 그 밖의 http·https 는 브라우저로 넘긴다([updateUrlTarget]).
 */
class AndroidExternalUrlOpener(
    private val context: Context,
    private val appExiter: AppExiter,
) : ExternalUrlOpener {

    private val apkFileName = "TalkUpdate.apk"

    private val _isDownloading = MutableStateFlow(false)
    override val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    override fun open(url: String) {
        if (!isWebUrl(url)) return
        openWithViewIntent(url)
    }

    override fun openUpdate(url: String) {
        when (updateUrlTarget(url, UpdateRule.ANDROID)) {
            UpdateUrlTarget.INSTALL -> downloadAndInstallApk(url.trim())
            UpdateUrlTarget.VIEW -> openWithViewIntent(url)
            UpdateUrlTarget.NONE -> Log.message("[UrlOpener/Android] update url not opened url=$url")
        }
    }

    /** 일반 URL·스토어 주소 — 링크로 열 수 있는 앱(`CATEGORY_BROWSABLE`, 브라우저·Play 스토어)에만 넘긴다. */
    private fun openWithViewIntent(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.trim())).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
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
                .setTitle(context.getString(R.string.update_download_title))
                .setDescription(context.getString(R.string.update_download_desc))
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

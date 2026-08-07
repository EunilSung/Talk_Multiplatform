package com.eunilsung.talk.data.remote.push

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.eunilsung.talk.domain.repository.SystemNotificationGate
import com.eunilsung.talk.util.Log

/** Android 구현 — 알림 설정 화면 이동 + 알림 활성화 여부 조회. */
class AndroidSystemNotificationGate(
    private val context: Context,
) : SystemNotificationGate {

    override fun openSettings() {
        runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { Log.message("[SystemNotificationGate/Android] openSettings failed: ${it.message}") }
    }

    override suspend fun isEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}

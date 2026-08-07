package com.eunilsung.talk.data.remote.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eunilsung.talk.R
import com.eunilsung.talk.MainActivity
import com.eunilsung.talk.domain.model.PushPayload
import com.eunilsung.talk.domain.repository.NotificationSettingsRepository
import com.eunilsung.talk.util.Log

/** Android [PushNotifier] 구현 — NotificationCompat + 사운드/진동 조합별 38개 NotificationChannel. */
class AndroidPushNotifier(
    private val context: Context,
    private val notificationSettings: NotificationSettingsRepository,
) : PushNotifier {

    init {
        ensureChannels()
    }

    override fun show(payload: PushPayload) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) {
            Log.message("[Push/Android] notifications disabled by user — POST_NOTIFICATIONS 권한 거부됨. 설정에서 허용 필요.")
            return
        }
        val (title, body) = formatTitleBody(payload)
        val noti = notificationSettings.state.value
        val channelId = selectChannel(
            sound = noti.sound,
            vibrate = noti.vibrate,
            soundId = noti.soundId,
        )
        Log.message("[Push/Android] show — channel=$channelId (sound=${noti.sound}, vibrate=${noti.vibrate}, soundId=${noti.soundId}) title='$title' body='${body.take(40)}' tag=${payload.msgKey}")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MSG_KIND, payload.kind.name)
            putExtra(EXTRA_MSG_KEY, payload.msgKey)
            putExtra(EXTRA_MSG_CATEGORY, payload.categoryId)
        }
        val pIntent = PendingIntent.getActivity(
            context,
            payload.msgKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.push_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .apply { if (payload.unreadCount > 0) setNumber(payload.unreadCount) }
            .setContentIntent(pIntent)
            .build()

        runCatching {
            nm.notify(payload.msgKey, NOTIFICATION_ID, notification)
            Log.message("[Push/Android] notify dispatched successfully")
        }.onFailure {
            Log.message("[Push/Android] notify failed: ${it.message}")
        }
    }

    override fun cancel(key: String) {
        NotificationManagerCompat.from(context).cancel(key, NOTIFICATION_ID)
    }

    override fun setBadge(count: Int) {
        val safe = count.coerceAtLeast(0)
        val applied = runCatching {
            me.leolin.shortcutbadger.ShortcutBadger.applyCount(context, safe)
        }.getOrElse { false }
        Log.message("[Push/Android] setBadge($safe) applied=$applied")
    }

    /** 사용자 설정(sound/vibrate/soundId) → 38채널 중 하나 선택. */
    private fun selectChannel(sound: Boolean, vibrate: Boolean, soundId: Int): String {
        return if (sound) {
            val safeId = soundId.coerceIn(0, NotificationSoundCatalog.SOUNDS.lastIndex)
            soundChannelId(safeId, vibrate)
        } else {
            muteChannelId(vibrate)
        }
    }

    /** 38채널을 미리 등록 (생성 후 sound/vibrate/importance 변경 불가). */
    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        NotificationSoundCatalog.SOUNDS.forEachIndexed { id, sound ->
            for (vib in listOf(true, false)) {
                val channelId = soundChannelId(id, vib)
                if (manager.getNotificationChannel(channelId) != null) continue
                val name = if (vib) "메시지 (${sound.displayName} + 진동)"
                else "메시지 (${sound.displayName})"
                val uri = soundUriFor(sound.baseName)
                val ch = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "사운드 ${sound.displayName} — ${if (vib) "진동 포함" else "진동 없음"}"
                    enableVibration(vib)
                    setSound(uri, audioAttrs)
                }
                manager.createNotificationChannel(ch)
            }
        }

        for (vib in listOf(true, false)) {
            val channelId = muteChannelId(vib)
            if (manager.getNotificationChannel(channelId) != null) continue
            val name = if (vib) "메시지 (진동)" else "메시지 (무음)"
            val ch = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = if (vib) "소리 없음 — 진동만" else "소리/진동 모두 끔"
                enableVibration(vib)
                setSound(null, null)
            }
            manager.createNotificationChannel(ch)
        }
    }

    /** soundId × vibrate → 채널 ID. */
    private fun soundChannelId(soundId: Int, vibrate: Boolean): String {
        val v = if (vibrate) "V1" else "V0"
        return "${CHANNEL_PREFIX_SOUND}${soundId}${v}_${CHANNEL_VERSION}"
    }

    /** 무음 채널 (vibrate O/X) → 채널 ID. */
    private fun muteChannelId(vibrate: Boolean): String {
        val v = if (vibrate) "V1" else "V0"
        return "${CHANNEL_PREFIX_MUTE}${v}_${CHANNEL_VERSION}"
    }

    /** `res/raw/{baseName}` 의 자원 URI — sound 가 wav/mp3/ogg 든 무관. */
    private fun soundUriFor(baseName: String): Uri =
        Uri.parse("android.resource://${context.packageName}/raw/$baseName")

    /** 푸시 종류별 title/body 구성. 미리보기 OFF 면 본문을 숨김 문구로 대체. */
    private fun formatTitleBody(payload: PushPayload): Pair<String, String> {
        val appName = context.getString(R.string.app_name)
        val title = payload.senderName.ifBlank { appName }
        val previewEnabled = notificationSettings.state.value.preview
        if (!previewEnabled) {
            return title to PushNotifier.PREVIEW_HIDDEN_BODY
        }
        return when (payload.kind) {
            PushPayload.Kind.CHAT -> title to payload.msg.ifBlank { "새 메시지" }
            else -> appName to (payload.msg.ifBlank { "새 알림" })
        }
    }

    companion object {
        const val CHANNEL_VERSION = "v3"
        const val CHANNEL_PREFIX_SOUND = "TALK_S"
        const val CHANNEL_PREFIX_MUTE = "TALK_M"

        /** notify 의 id 슬롯 — tag(msgKey) 로만 구분하므로 0 고정. */
        private const val NOTIFICATION_ID = 0

        /** 알림 탭 → MainActivity intent extras 키. */
        const val EXTRA_MSG_KIND = "talk_push_msgkind"
        const val EXTRA_MSG_KEY = "talk_push_msgkey"
        const val EXTRA_MSG_CATEGORY = "talk_push_msgcategory"
    }
}

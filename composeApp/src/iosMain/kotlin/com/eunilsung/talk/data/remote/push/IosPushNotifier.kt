package com.eunilsung.talk.data.remote.push

import kotlinx.cinterop.ExperimentalForeignApi
import com.eunilsung.talk.domain.model.PushPayload
import com.eunilsung.talk.domain.repository.NotificationSettingsRepository
import com.eunilsung.talk.util.Log
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/** iOS [PushNotifier] 구현 — `UNUserNotificationCenter` 로컬 알림. */
@OptIn(ExperimentalForeignApi::class)
class IosPushNotifier(
    private val notificationSettings: NotificationSettingsRepository,
) : PushNotifier {

    override fun show(payload: PushPayload) {
        val (title, body) = formatTitleBody(payload)
        val userInfoMap: Map<Any?, Any?> = mapOf(
            USER_INFO_KIND to payload.kind.name,
            USER_INFO_KEY to payload.msgKey,
            USER_INFO_CATEGORY to payload.categoryId,
        )
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
            setUserInfo(userInfoMap)
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = payload.msgKey,
            content = content,
            trigger = null
        )
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
            if (error != null) {
                Log.message("[Push/iOS] addNotificationRequest failed: ${error.localizedDescription}")
            }
        }
    }

    /** identifier 또는 userInfo 의 msgkey 가 [key] 와 일치하는 배달된 알림 제거. */
    override fun cancel(key: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val ids = delivered.orEmpty().mapNotNull { item ->
                val notification = item as? UNNotification ?: return@mapNotNull null
                val request = notification.request
                val info = request.content.userInfo
                val infoKey = (info["msgkey"] as? String) ?: (info["talk_push_msgkey"] as? String)
                if (request.identifier == key || infoKey == key) request.identifier else null
            }
            Log.message("[Push/iOS] cancel key=$key matched=${ids.size}")
            if (ids.isNotEmpty()) {
                center.removeDeliveredNotificationsWithIdentifiers(ids)
            }
        }
    }

    /** 런처 아이콘 배지 갱신. */
    override fun setBadge(count: Int) {
        UNUserNotificationCenter.currentNotificationCenter()
            .setBadgeCount(count.toLong(), null)
    }

    /** 미리보기 토글이 OFF 면 본문을 [PushNotifier.PREVIEW_HIDDEN_BODY] 로 대체. 제목은 유지. */
    private fun formatTitleBody(payload: PushPayload): Pair<String, String> {
        val title = payload.senderName.ifBlank { "Talk" }
        val previewEnabled = notificationSettings.state.value.preview
        if (!previewEnabled) {
            return title to PushNotifier.PREVIEW_HIDDEN_BODY
        }
        return when (payload.kind) {
            PushPayload.Kind.CHAT -> title to payload.msg.ifBlank { "새 메시지" }
            else -> "Talk" to payload.msg.ifBlank { "새 알림" }
        }
    }

    companion object {
        const val USER_INFO_KIND = "talk_push_msgkind"
        const val USER_INFO_KEY = "talk_push_msgkey"
        const val USER_INFO_CATEGORY = "talk_push_msgcategory"
    }
}

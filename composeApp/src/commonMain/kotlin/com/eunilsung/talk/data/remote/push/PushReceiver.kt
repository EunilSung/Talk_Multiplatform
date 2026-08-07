package com.eunilsung.talk.data.remote.push

import com.eunilsung.talk.domain.model.PushPayload
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.util.Log
import org.koin.mp.KoinPlatform

/** 푸시 메시지 처리 진입점 — Android/iOS 가 공통으로 호출. */
object PushReceiver {

    fun onPushReceived(
        msg: String,
        msgKey: String,
        msgKind: String,
        msgType: String,
        senderName: String,
        unreadCount: String,
        categoryId: String,
    ) {
        val koin = runCatching { KoinPlatform.getKoin() }.getOrNull() ?: return
        val parser: PushPayloadParser = koin.get()
        val notifier: PushNotifier = koin.get()
        val loginRepository: LoginRepository = koin.get()

        val rawMap = mapOf(
            PushPayloadParser.KEY_MSG to msg,
            PushPayloadParser.KEY_MSG_KEY to msgKey,
            PushPayloadParser.KEY_MSG_KIND to msgKind,
            PushPayloadParser.KEY_MSGTYPE to msgType,
            PushPayloadParser.KEY_SENDER_NAME to senderName,
            PushPayloadParser.KEY_UNREAD_COUNT to unreadCount,
            PushPayloadParser.KEY_CATEGORY_ID to categoryId,
        )
        val payload = parser.parse(rawMap)
        Log.message(
            "[Push] received: kind=${payload.kind} key=${payload.msgKey} " +
                    "sender=${payload.senderName} msgType=${payload.msgType} " +
                    "body='${payload.msg.take(40)}' unread=${payload.unreadCount}"
        )

        if (!loginRepository.isForeground.value && payload.unreadCount > 0) {
            notifier.setBadge(payload.unreadCount)
        }

        when (payload.kind) {
            PushPayload.Kind.BADGE -> {
                return
            }
            PushPayload.Kind.UNKNOWN -> {
                Log.message("[Push] skip — unknown kind")
                return
            }
            PushPayload.Kind.CHAT -> {
                if (payload.msgType == PushPayload.CHAT_TYPE_INVITE ||
                    payload.msgType == PushPayload.CHAT_TYPE_EXIT) {
                    Log.message("[Push] skip CHAT — system message msgType=${payload.msgType}")
                    return
                }
                if (CurrentChatRoomTracker.currentChatRoomId == payload.msgKey
                    && loginRepository.isForeground.value
                ) {
                    Log.message("[Push] skip CHAT — user is already in room ${payload.msgKey}")
                    return
                }
            }
        }

        Log.message("[Push] calling notifier.show() for key=${payload.msgKey}")
        notifier.show(payload)
    }

    /** iOS `willPresent` 전용 — 보고 있는 대화방의 foreground CHAT 푸시 표시를 억제할지 여부. */
    fun shouldSuppressForegroundChat(msgKind: String, msgKey: String): Boolean {
        val koin = runCatching { KoinPlatform.getKoin() }.getOrNull() ?: return false
        val parser: PushPayloadParser = koin.get()
        val loginRepository: LoginRepository = koin.get()
        val kind = parser.parse(
            mapOf(
                PushPayloadParser.KEY_MSG_KIND to msgKind,
                PushPayloadParser.KEY_MSG_KEY to msgKey,
            )
        ).kind
        return kind == PushPayload.Kind.CHAT &&
            CurrentChatRoomTracker.currentChatRoomId == msgKey &&
            loginRepository.isForeground.value
    }
}

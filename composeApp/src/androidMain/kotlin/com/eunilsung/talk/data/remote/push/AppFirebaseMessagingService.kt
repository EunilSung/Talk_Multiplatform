package com.eunilsung.talk.data.remote.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.eunilsung.talk.util.Log

/** Android FCM 진입점 — 토큰 갱신 및 푸시 수신 처리. */
class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.message("[Push/Android] onNewToken: ${token}")
        PushTokenBridge.onTokenReceived(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.message("[Push/Android] onMessageReceived: data=${message.data}")
        val data = message.data
        PushReceiver.onPushReceived(
            msg = data[PushPayloadParser.KEY_MSG].orEmpty(),
            msgKey = data[PushPayloadParser.KEY_MSG_KEY].orEmpty(),
            msgKind = data[PushPayloadParser.KEY_MSG_KIND].orEmpty(),
            msgType = data[PushPayloadParser.KEY_MSGTYPE].orEmpty(),
            senderName = data[PushPayloadParser.KEY_SENDER_NAME].orEmpty(),
            unreadCount = data[PushPayloadParser.KEY_UNREAD_COUNT].orEmpty(),
            categoryId = data[PushPayloadParser.KEY_CATEGORY_ID].orEmpty(),
        )
    }
}

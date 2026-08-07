package com.eunilsung.talk.data.sample

import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.repository.SenderOverrideRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 대화방마다 전송 주체(누구 이름으로 보낼지) 를 기억한다. 샘플 전용, 저장 안 함. */
class ChatSenderOverride : SenderOverrideRepository {

    private val _senders = MutableStateFlow<Map<String, User>>(emptyMap())

    /** 대화방 id → 대신 보낼 사용자. 항목이 없으면 "나". */
    override val senders: StateFlow<Map<String, User>> = _senders.asStateFlow()

    /** [chatRoomId] 의 전송 주체. `null` 이면 나로 보낸다. */
    override fun senderFor(chatRoomId: String): User? = _senders.value[chatRoomId]

    /** 전송 주체를 바꾼다. [user] 가 null 이거나 나 자신이면 해제. */
    override fun select(chatRoomId: String, user: User?) {
        if (chatRoomId.isBlank()) return
        val isMe = user == null || user.id.isBlank() || user.id == Config.MyInfo.userId
        _senders.value = _senders.value.toMutableMap().apply {
            if (isMe) remove(chatRoomId) else put(chatRoomId, user!!)
        }
    }
}

package com.eunilsung.talk.domain.repository

import com.eunilsung.talk.domain.model.User
import kotlinx.coroutines.flow.StateFlow

/**
 * 대화방마다 "누구 이름으로 보낼지" 를 기억한다 — 샘플 시연용.
 *
 * 서버가 없어 상대방 대화를 만들어 볼 수 없으므로, 전송 주체를 바꿔 대화 흐름을 재현한다.
 */
interface SenderOverrideRepository {
    /** 대화방 id → 대신 보낼 사용자. 항목이 없으면 "나". */
    val senders: StateFlow<Map<String, User>>

    fun senderFor(chatRoomId: String): User?

    /** [user] 가 null 이거나 나 자신이면 해제. */
    fun select(chatRoomId: String, user: User?)
}

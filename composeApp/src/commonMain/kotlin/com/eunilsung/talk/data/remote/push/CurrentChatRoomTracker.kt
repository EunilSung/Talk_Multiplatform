package com.eunilsung.talk.data.remote.push

import kotlinx.atomicfu.atomic
import com.eunilsung.talk.domain.repository.CurrentChatRoomRepository

/** 현재 진입 중인 대화방 ID 를 전역 추적 — 푸시 알림 억제용. */
object CurrentChatRoomTracker : CurrentChatRoomRepository {
    private val ref = atomic<String?>(null)

    override val currentChatRoomId: String? get() = ref.value

    override fun set(chatRoomId: String) {
        ref.value = chatRoomId
    }

    override fun clear() {
        ref.value = null
    }
}

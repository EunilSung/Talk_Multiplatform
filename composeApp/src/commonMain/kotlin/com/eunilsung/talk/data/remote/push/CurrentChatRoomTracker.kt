package com.eunilsung.talk.data.remote.push

import kotlinx.atomicfu.atomic

/** 현재 진입 중인 대화방 ID 를 전역 추적 — 푸시 알림 억제용. */
object CurrentChatRoomTracker {
    private val ref = atomic<String?>(null)

    val currentChatRoomId: String? get() = ref.value

    fun set(chatRoomId: String) {
        ref.value = chatRoomId
    }

    fun clear() {
        ref.value = null
    }
}

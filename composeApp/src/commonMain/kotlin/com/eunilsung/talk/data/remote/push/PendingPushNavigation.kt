package com.eunilsung.talk.data.remote.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 푸시 알림 탭 시 열어야 할 화면의 대기 큐. */
object PendingPushNavigation {

    private val _pending = MutableStateFlow<PushNavRequest?>(null)
    val pending: StateFlow<PushNavRequest?> = _pending.asStateFlow()

    /** 푸시 탭 발생 시 호출. 같은 키가 이미 있으면 덮어쓰기 (최신만 처리). */
    fun set(kind: String, msgKey: String, categoryId: String) {
        if (msgKey.isBlank()) return
        _pending.value = PushNavRequest(
            kind = kind.uppercase(),
            msgKey = msgKey,
            categoryId = categoryId,
        )
    }

    /** 처리 완료 — 큐 비움. consume 후 즉시 라우팅 수행해야 함. */
    fun consume(): PushNavRequest? {
        val current = _pending.value
        _pending.value = null
        return current
    }
}

/**
 * 푸시 라우팅 요청.
 *
 * @param kind "TALK" / "MSG" / "BADGE" 등 (대문자 정규화 후)
 * @param msgKey 대화방 ID
 */
data class PushNavRequest(
    val kind: String,
    val msgKey: String,
    val categoryId: String,
)

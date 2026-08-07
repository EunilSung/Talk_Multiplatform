package com.eunilsung.talk.data.remote.share

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 외부 앱 공유 시트로 진입한 데이터의 대기 큐. */
object PendingShareNavigation {

    private val _pending = MutableStateFlow<SharedContent?>(null)
    val pending: StateFlow<SharedContent?> = _pending.asStateFlow()

    /** 공유 진입 시 호출. 빈 content 는 무시, 기존 값은 덮어쓴다. */
    fun set(content: SharedContent) {
        if (content.isEmpty) return
        _pending.value = content
    }

    /** 처리 완료 — 큐 비움. */
    fun consume(): SharedContent? {
        val current = _pending.value
        _pending.value = null
        return current
    }
}

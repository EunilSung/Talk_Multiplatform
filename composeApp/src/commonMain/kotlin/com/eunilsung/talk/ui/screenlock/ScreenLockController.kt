package com.eunilsung.talk.ui.screenlock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 앱 전역 화면 잠금 상태 싱글턴 — 포그라운드 복귀 시 즉시 잠금. */
object ScreenLockController {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var initialized = false

    /** 앱 최초 진입 시 1회 — 잠금 사용 중이면 잠금 상태로. */
    fun ensureInitialLock(enabled: Boolean) {
        if (initialized) return
        initialized = true
        if (enabled) _locked.value = true
    }

    /** 백그라운드 진입 — 잠금 사용 중이면 다음 포그라운드에서 잠금. */
    fun onBackground(enabled: Boolean) {
        if (enabled) _locked.value = true
    }

    /** 인증 성공 → 잠금 해제. */
    fun unlock() {
        _locked.value = false
    }

    /** 로그아웃/잠금 미사용 전환 시 초기화. */
    fun reset() {
        initialized = false
        _locked.value = false
    }
}

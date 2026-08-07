package com.eunilsung.talk.domain.repository

/** OS 레벨 알림 권한 게이트 — 시스템 권한 상태를 미러한다. */
interface SystemNotificationGate {

    /** 시스템 알림 설정 화면으로 이동 (best-effort). */
    fun openSettings()

    /** 현재 시스템 알림 권한 활성 여부. */
    suspend fun isEnabled(): Boolean
}

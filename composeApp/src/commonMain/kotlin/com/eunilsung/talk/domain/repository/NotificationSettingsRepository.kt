package com.eunilsung.talk.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** 알림 관련 사용자 설정 저장/조회. */
interface NotificationSettingsRepository {

    /** 현재 알림 설정 스냅샷. */
    val state: StateFlow<NotificationSettingsState>

    fun setPreview(enabled: Boolean)

    /**
     * 알림 시간 설정. enabled=true 이면 [value]="시작hhmm+종료hhmm"(예 "09001800").
     * @return 저장 성공 여부 — false 면 설정이 바뀌지 않는다.
     */
    suspend fun setTimeLimit(enabled: Boolean, value: String): Boolean

    /**
     * PC 로그인 후 알림 받지 않음 설정.
     * @return 저장 성공 여부 — false 면 토글이 되돌아간다.
     */
    suspend fun setPcOff(enabled: Boolean): Boolean

    fun setSound(enabled: Boolean)
    fun setVibrate(enabled: Boolean)

    /** 선택한 알림음 ID 저장 (0=기본). */
    fun setSoundId(id: Int)

    /** 시스템 알림 권한 상태를 조회해 `enabled` 필드만 동기화. */
    suspend fun syncEnabledFromSystem()
}

/** UI 가 collect 할 알림 설정 묶음. */
data class NotificationSettingsState(
    val enabled: Boolean = true,
    val preview: Boolean = true,
    val timeLimit: Boolean = false,
    /** 알림 시간 설정 값 (형식: 시작hhmm+종료hhmm, 예 "09001800"). off 이면 빈 문자열. */
    val timeLimitValue: String = "",
    val pcOff: Boolean = false,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
    val soundId: Int = 0,
)

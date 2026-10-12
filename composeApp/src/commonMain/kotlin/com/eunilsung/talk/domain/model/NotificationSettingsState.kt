package com.eunilsung.talk.domain.model

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

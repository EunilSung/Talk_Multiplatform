package com.eunilsung.talk.ui.setting

import com.eunilsung.talk.domain.model.ChatSettingsState
import com.eunilsung.talk.domain.model.NotificationSettingsState

/** 환경설정 화면의 상태 — 기기에 저장된 알림·대화 설정을 그대로 비춘다. */
data class SettingUiState(
    val notification: NotificationSettingsState = NotificationSettingsState(),
    val chat: ChatSettingsState = ChatSettingsState(),
    /** 시스템 알림 설정처럼 Android 에만 있는 항목을 보일지. */
    val isAndroid: Boolean = true,
)

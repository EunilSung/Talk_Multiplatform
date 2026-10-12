package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.repository.ChatSettingsRepository
import com.eunilsung.talk.domain.model.ChatSettingsState
import com.eunilsung.talk.domain.repository.NotificationSettingsRepository
import com.eunilsung.talk.domain.model.NotificationSettingsState
import com.eunilsung.talk.domain.repository.NotificationSoundPreviewer
import com.eunilsung.talk.domain.repository.SystemNotificationGate
import kotlinx.coroutines.flow.StateFlow

/** 환경설정 화면의 유스케이스 묶음. */
data class SettingUseCases(
    val observeNotificationSettings: ObserveNotificationSettingsUseCase,
    val syncNotificationFromSystem: SyncNotificationFromSystemUseCase,
    val openSystemNotificationSettings: OpenSystemNotificationSettingsUseCase,
    val setNotificationPreview: SetNotificationPreviewUseCase,
    val setNotificationTimeLimit: SetNotificationTimeLimitUseCase,
    val setNotificationPcOff: SetNotificationPcOffUseCase,
    val setNotificationSound: SetNotificationSoundUseCase,
    val setNotificationVibrate: SetNotificationVibrateUseCase,
    val setNotificationSoundId: SetNotificationSoundIdUseCase,
    val playSoundPreview: PlaySoundPreviewUseCase,
    val stopSoundPreview: StopSoundPreviewUseCase,
    val observeChatSettings: ObserveChatSettingsUseCase,
    val setChatEnterToSend: SetChatEnterToSendUseCase,
    val setChatFontSize: SetChatFontSizeUseCase,
)

/** 알림 설정 구독. */
class ObserveNotificationSettingsUseCase(private val repository: NotificationSettingsRepository) {
    operator fun invoke(): StateFlow<NotificationSettingsState> = repository.state
}

/** 기기의 알림 허용 상태를 앱 설정에 맞춘다. */
class SyncNotificationFromSystemUseCase(private val repository: NotificationSettingsRepository) {
    suspend operator fun invoke() = repository.syncEnabledFromSystem()
}

/** 기기의 알림 설정 화면을 연다. */
class OpenSystemNotificationSettingsUseCase(private val gate: SystemNotificationGate) {
    operator fun invoke() = gate.openSettings()
}

/** 알림에 내용을 보일지. */
class SetNotificationPreviewUseCase(private val repository: NotificationSettingsRepository) {
    operator fun invoke(enabled: Boolean) = repository.setPreview(enabled)
}

/** 알림 시간 제한. 받아들여졌으면 true. */
class SetNotificationTimeLimitUseCase(private val repository: NotificationSettingsRepository) {
    suspend operator fun invoke(enabled: Boolean, value: String): Boolean = repository.setTimeLimit(enabled, value)
}

/** PC 접속 중 알림 끄기. 받아들여졌으면 true. */
class SetNotificationPcOffUseCase(private val repository: NotificationSettingsRepository) {
    suspend operator fun invoke(enabled: Boolean): Boolean = repository.setPcOff(enabled)
}

/** 알림 소리. */
class SetNotificationSoundUseCase(private val repository: NotificationSettingsRepository) {
    operator fun invoke(enabled: Boolean) = repository.setSound(enabled)
}

/** 알림 진동. */
class SetNotificationVibrateUseCase(private val repository: NotificationSettingsRepository) {
    operator fun invoke(enabled: Boolean) = repository.setVibrate(enabled)
}

/** 알림 소리 종류. */
class SetNotificationSoundIdUseCase(private val repository: NotificationSettingsRepository) {
    operator fun invoke(id: Int) = repository.setSoundId(id)
}

/** 알림 소리 미리듣기. */
class PlaySoundPreviewUseCase(private val previewer: NotificationSoundPreviewer) {
    operator fun invoke(soundId: Int) = previewer.play(soundId)
}

/** 미리듣기 멈춤. */
class StopSoundPreviewUseCase(private val previewer: NotificationSoundPreviewer) {
    operator fun invoke() = previewer.stop()
}

/** 대화 설정(엔터로 보내기·글자 크기) 구독. */
class ObserveChatSettingsUseCase(private val repository: ChatSettingsRepository) {
    operator fun invoke(): StateFlow<ChatSettingsState> = repository.state
}

/** 엔터로 보내기. */
class SetChatEnterToSendUseCase(private val repository: ChatSettingsRepository) {
    operator fun invoke(enabled: Boolean) = repository.setEnterToSend(enabled)
}

/** 대화 글자 크기. */
class SetChatFontSizeUseCase(private val repository: ChatSettingsRepository) {
    operator fun invoke(size: Int) = repository.setFontSize(size)
}

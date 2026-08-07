package com.eunilsung.talk.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.eunilsung.talk.Config
import com.eunilsung.talk.Platform
import com.eunilsung.talk.data.remote.push.NotificationSoundPreviewer
import com.eunilsung.talk.domain.repository.ChatSettingsRepository
import com.eunilsung.talk.domain.repository.ChatSettingsState
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.domain.repository.NotificationSettingsRepository
import com.eunilsung.talk.domain.repository.NotificationSettingsState
import com.eunilsung.talk.domain.repository.SystemNotificationGate
import com.eunilsung.talk.util.Log

class SettingViewModel(
    private val loginRepository: LoginRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val systemNotificationGate: SystemNotificationGate,
    private val platform: Platform,
    private val soundPreviewer: NotificationSoundPreviewer,
) : ViewModel() {

    val notificationSettings: StateFlow<NotificationSettingsState> =
        notificationSettingsRepository.state
    val chatSettings: StateFlow<ChatSettingsState> =
        chatSettingsRepository.state

    val isAndroid: Boolean = platform.name != "IPHONE"

    private val _toastEvents = Channel<String>(Channel.BUFFERED)
    val toastEvents: Flow<String> = _toastEvents.receiveAsFlow()

    fun playSoundPreview(soundId: Int) {
        soundPreviewer.play(soundId)
    }

    fun stopSoundPreview() {
        soundPreviewer.stop()
    }

    override fun onCleared() {
        super.onCleared()
        soundPreviewer.stop()
    }

    fun onAction(action: SettingActions) {
        when (action) {
            is SettingActions.Logout -> loginRepository.logout()

            is SettingActions.OpenSystemNotificationSettings ->
                systemNotificationGate.openSettings()
            is SettingActions.SyncSystemNotificationState ->
                viewModelScope.launch {
                    runCatching { notificationSettingsRepository.syncEnabledFromSystem() }
                        .onFailure { Log.message("[Setting] syncEnabledFromSystem failed: ${it.message}") }
                }
            is SettingActions.SetNotificationPreview ->
                notificationSettingsRepository.setPreview(action.enabled)
            is SettingActions.SetNotificationTimeLimit ->
                viewModelScope.launch {
                    val confirmed = runCatching {
                        notificationSettingsRepository.setTimeLimit(action.enabled, action.value)
                    }.getOrDefault(false)
                    if (!confirmed) _toastEvents.trySend("설정 변경에 실패했습니다.")
                }
            is SettingActions.SetNotificationPcOff ->
                viewModelScope.launch {
                    val confirmed = runCatching { notificationSettingsRepository.setPcOff(action.enabled) }
                        .getOrDefault(false)
                    if (!confirmed) _toastEvents.trySend("설정 변경에 실패했습니다.")
                }
            is SettingActions.SetNotificationSound ->
                notificationSettingsRepository.setSound(action.enabled)
            is SettingActions.SetNotificationVibrate ->
                notificationSettingsRepository.setVibrate(action.enabled)
            is SettingActions.SetNotificationSoundId ->
                notificationSettingsRepository.setSoundId(action.id)

            is SettingActions.SetChatEnterSend ->
                chatSettingsRepository.setEnterToSend(action.enabled)
            is SettingActions.SetChatFontSize ->
                chatSettingsRepository.setFontSize(action.size)
        }
    }

}

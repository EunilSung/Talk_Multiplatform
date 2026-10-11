package com.eunilsung.talk.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.eunilsung.talk.Platform
import com.eunilsung.talk.domain.repository.ChatSettingsState
import com.eunilsung.talk.domain.repository.NotificationSettingsState
import com.eunilsung.talk.util.Log
import com.eunilsung.talk.domain.usecase.SettingUseCases
import com.eunilsung.talk.domain.usecase.LoginUseCases

class SettingViewModel(
    private val settingUseCases: SettingUseCases,
    private val loginUseCases: LoginUseCases,
    private val platform: Platform,
) : ViewModel() {

    val notificationSettings: StateFlow<NotificationSettingsState> =
        settingUseCases.observeNotificationSettings()
    val chatSettings: StateFlow<ChatSettingsState> =
        settingUseCases.observeChatSettings()

    val isAndroid: Boolean = platform.name != "IPHONE"

    private val _toastEvents = Channel<String>(Channel.BUFFERED)
    val toastEvents: Flow<String> = _toastEvents.receiveAsFlow()

    fun playSoundPreview(soundId: Int) {
        settingUseCases.playSoundPreview(soundId)
    }

    fun stopSoundPreview() {
        settingUseCases.stopSoundPreview()
    }

    override fun onCleared() {
        super.onCleared()
        settingUseCases.stopSoundPreview()
    }

    fun onAction(action: SettingActions) {
        when (action) {
            is SettingActions.Logout -> loginUseCases.logout()

            is SettingActions.OpenSystemNotificationSettings ->
                settingUseCases.openSystemNotificationSettings()
            is SettingActions.SyncSystemNotificationState ->
                viewModelScope.launch {
                    runCatching { settingUseCases.syncNotificationFromSystem() }
                        .onFailure { Log.message("[Setting] syncEnabledFromSystem failed: ${it.message}") }
                }
            is SettingActions.SetNotificationPreview ->
                settingUseCases.setNotificationPreview(action.enabled)
            is SettingActions.SetNotificationTimeLimit ->
                viewModelScope.launch {
                    val confirmed = runCatching {
                        settingUseCases.setNotificationTimeLimit(action.enabled, action.value)
                    }.getOrDefault(false)
                    if (!confirmed) _toastEvents.trySend("설정 변경에 실패했습니다.")
                }
            is SettingActions.SetNotificationPcOff ->
                viewModelScope.launch {
                    val confirmed = runCatching { settingUseCases.setNotificationPcOff(action.enabled) }
                        .getOrDefault(false)
                    if (!confirmed) _toastEvents.trySend("설정 변경에 실패했습니다.")
                }
            is SettingActions.SetNotificationSound ->
                settingUseCases.setNotificationSound(action.enabled)
            is SettingActions.SetNotificationVibrate ->
                settingUseCases.setNotificationVibrate(action.enabled)
            is SettingActions.SetNotificationSoundId ->
                settingUseCases.setNotificationSoundId(action.id)

            is SettingActions.SetChatEnterSend ->
                settingUseCases.setChatEnterToSend(action.enabled)
            is SettingActions.SetChatFontSize ->
                settingUseCases.setChatFontSize(action.size)
        }
    }

}

package com.eunilsung.talk.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.eunilsung.talk.Platform
import com.eunilsung.talk.util.Log
import com.eunilsung.talk.domain.usecase.SettingUseCases
import com.eunilsung.talk.domain.usecase.LoginUseCases

class SettingViewModel(
    private val settingUseCases: SettingUseCases,
    private val loginUseCases: LoginUseCases,
    platform: Platform,
) : ViewModel() {

    private val isAndroid: Boolean = platform.name != "IPHONE"

    val uiState: StateFlow<SettingUiState> =
        combine(
            settingUseCases.observeNotificationSettings(),
            settingUseCases.observeChatSettings(),
        ) { notification, chat ->
            SettingUiState(notification = notification, chat = chat, isAndroid = isAndroid)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingUiState(
                notification = settingUseCases.observeNotificationSettings().value,
                chat = settingUseCases.observeChatSettings().value,
                isAndroid = isAndroid,
            ),
        )

    private val _events = Channel<SettingEvent>(Channel.BUFFERED)
    val events: Flow<SettingEvent> = _events.receiveAsFlow()

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
                    if (!confirmed) _events.trySend(SettingEvent.ChangeFailed)
                }
            is SettingActions.SetNotificationPcOff ->
                viewModelScope.launch {
                    val confirmed = runCatching { settingUseCases.setNotificationPcOff(action.enabled) }
                        .getOrDefault(false)
                    if (!confirmed) _events.trySend(SettingEvent.ChangeFailed)
                }
            is SettingActions.SetNotificationSound ->
                settingUseCases.setNotificationSound(action.enabled)
            is SettingActions.SetNotificationVibrate ->
                settingUseCases.setNotificationVibrate(action.enabled)
            is SettingActions.SetNotificationSoundId ->
                settingUseCases.setNotificationSoundId(action.id)
            is SettingActions.PlaySoundPreview ->
                settingUseCases.playSoundPreview(action.soundId)
            is SettingActions.StopSoundPreview ->
                settingUseCases.stopSoundPreview()

            is SettingActions.SetChatEnterSend ->
                settingUseCases.setChatEnterToSend(action.enabled)
            is SettingActions.SetChatFontSize ->
                settingUseCases.setChatFontSize(action.size)
        }
    }

}

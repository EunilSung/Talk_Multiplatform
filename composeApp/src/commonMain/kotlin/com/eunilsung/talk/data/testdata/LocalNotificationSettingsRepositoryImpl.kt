package com.eunilsung.talk.data.testdata

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.eunilsung.talk.data.repository.SettingsKeys
import com.eunilsung.talk.data.repository.setAppGroupNotificationPreview
import com.eunilsung.talk.data.repository.setAppGroupNotificationSoundEnabled
import com.eunilsung.talk.data.repository.setAppGroupNotificationSoundId
import com.eunilsung.talk.domain.repository.NotificationSettingsRepository
import com.eunilsung.talk.domain.repository.NotificationSettingsState
import com.eunilsung.talk.domain.repository.SystemNotificationGate
import com.eunilsung.talk.util.Log

/** 알림 설정 — 전 항목을 [Settings] 에만 저장한다. */
class LocalNotificationSettingsRepositoryImpl(
    private val settings: Settings,
    private val systemGate: SystemNotificationGate,
) : NotificationSettingsRepository {

    private val _state = MutableStateFlow(loadFromSettings())
    override val state: StateFlow<NotificationSettingsState> = _state.asStateFlow()

    init {
        val s = _state.value
        setAppGroupNotificationPreview(s.preview)
        setAppGroupNotificationSoundEnabled(s.sound)
        setAppGroupNotificationSoundId(s.soundId)
    }

    private fun loadFromSettings(): NotificationSettingsState = NotificationSettingsState(
        enabled = settings.getBoolean(SettingsKeys.KEY_NOTI_ENABLED, true),
        preview = settings.getBoolean(SettingsKeys.KEY_NOTI_PREVIEW, true),
        timeLimit = settings.getBoolean(SettingsKeys.KEY_NOTI_TIME_LIMIT, false),
        timeLimitValue = settings.getString(SettingsKeys.KEY_NOTI_TIME_VALUE, ""),
        pcOff = settings.getBoolean(SettingsKeys.KEY_NOTI_PC_OFF, false),
        sound = settings.getBoolean(SettingsKeys.KEY_NOTI_SOUND, true),
        vibrate = settings.getBoolean(SettingsKeys.KEY_NOTI_VIBRATE, true),
        soundId = settings.getInt(SettingsKeys.KEY_NOTI_SOUND_ID, 0),
    )

    override fun setPreview(enabled: Boolean) {
        settings.putBoolean(SettingsKeys.KEY_NOTI_PREVIEW, enabled)
        _state.value = _state.value.copy(preview = enabled)
        setAppGroupNotificationPreview(enabled)
    }

    /** 바로 반영하고 성공 처리. */
    override suspend fun setTimeLimit(enabled: Boolean, value: String): Boolean {
        val stored = if (enabled) value else ""
        settings.putBoolean(SettingsKeys.KEY_NOTI_TIME_LIMIT, enabled)
        settings.putString(SettingsKeys.KEY_NOTI_TIME_VALUE, stored)
        _state.value = _state.value.copy(timeLimit = enabled, timeLimitValue = stored)
        Log.message("[NotiSettings/Local] timeLimit=$enabled value='$stored'")
        return true
    }

    override suspend fun setPcOff(enabled: Boolean): Boolean {
        settings.putBoolean(SettingsKeys.KEY_NOTI_PC_OFF, enabled)
        _state.value = _state.value.copy(pcOff = enabled)
        Log.message("[NotiSettings/Local] pcOff=$enabled")
        return true
    }

    override fun setSound(enabled: Boolean) {
        settings.putBoolean(SettingsKeys.KEY_NOTI_SOUND, enabled)
        _state.value = _state.value.copy(sound = enabled)
        setAppGroupNotificationSoundEnabled(enabled)
    }

    override fun setVibrate(enabled: Boolean) {
        settings.putBoolean(SettingsKeys.KEY_NOTI_VIBRATE, enabled)
        _state.value = _state.value.copy(vibrate = enabled)
    }

    override fun setSoundId(id: Int) {
        settings.putInt(SettingsKeys.KEY_NOTI_SOUND_ID, id)
        _state.value = _state.value.copy(soundId = id)
        setAppGroupNotificationSoundId(id)
    }

    override suspend fun syncEnabledFromSystem() {
        val current = systemGate.isEnabled()
        if (_state.value.enabled == current) return
        settings.putBoolean(SettingsKeys.KEY_NOTI_ENABLED, current)
        _state.value = _state.value.copy(enabled = current)
    }
}

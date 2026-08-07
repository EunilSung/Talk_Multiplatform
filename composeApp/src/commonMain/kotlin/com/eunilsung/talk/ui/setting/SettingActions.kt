package com.eunilsung.talk.ui.setting

import com.eunilsung.talk.domain.repository.ChatSettingsRepository

sealed interface SettingActions {
    data object Logout : SettingActions

    data object OpenSystemNotificationSettings : SettingActions
    data object SyncSystemNotificationState : SettingActions
    data class SetNotificationPreview(val enabled: Boolean) : SettingActions
    /** value: 켤 때 "시작hhmm+종료hhmm"(예 "09001800"), 끌 때 "0". */
    data class SetNotificationTimeLimit(val enabled: Boolean, val value: String) : SettingActions
    data class SetNotificationPcOff(val enabled: Boolean) : SettingActions
    data class SetNotificationSound(val enabled: Boolean) : SettingActions
    data class SetNotificationVibrate(val enabled: Boolean) : SettingActions
    data class SetNotificationSoundId(val id: Int) : SettingActions

    data class SetChatEnterSend(val enabled: Boolean) : SettingActions
    data class SetChatFontSize(val size: Int) : SettingActions

}

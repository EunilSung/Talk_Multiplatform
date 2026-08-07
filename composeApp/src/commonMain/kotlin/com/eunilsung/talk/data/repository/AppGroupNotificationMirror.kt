package com.eunilsung.talk.data.repository

/** 알림 설정을 App Group NSUserDefaults 에 미러하는 헬퍼 (iOS 는 저장, Android 는 no-op). */
internal expect fun setAppGroupNotificationPreview(enabled: Boolean)
internal expect fun setAppGroupNotificationSoundEnabled(enabled: Boolean)
internal expect fun setAppGroupNotificationSoundId(id: Int)

/** App Group identifier. */
internal const val APP_GROUP_ID = "group.com.eunilsung.talk"

/** App Group UserDefaults 에 저장될 키. */
internal const val APP_GROUP_KEY_NOTI_PREVIEW = "noti_preview"
internal const val APP_GROUP_KEY_NOTI_SOUND_ENABLED = "noti_sound_enabled"
internal const val APP_GROUP_KEY_NOTI_SOUND_ID = "noti_sound_id"

package com.eunilsung.talk.data.repository

import com.eunilsung.talk.util.Log
import platform.Foundation.NSUserDefaults

/** iOS App Group `NSUserDefaults` 에 알림 미리보기 설정 미러. */
internal actual fun setAppGroupNotificationPreview(enabled: Boolean) {
    appGroupDefaults()?.setBool(enabled, forKey = APP_GROUP_KEY_NOTI_PREVIEW)
}

internal actual fun setAppGroupNotificationSoundEnabled(enabled: Boolean) {
    appGroupDefaults()?.setBool(enabled, forKey = APP_GROUP_KEY_NOTI_SOUND_ENABLED)
}

internal actual fun setAppGroupNotificationSoundId(id: Int) {
    appGroupDefaults()?.setInteger(id.toLong(), forKey = APP_GROUP_KEY_NOTI_SOUND_ID)
}

private fun appGroupDefaults(): NSUserDefaults? {
    val prefs = NSUserDefaults(suiteName = APP_GROUP_ID)
    if (prefs == null) {
        Log.message("[AppGroup/iOS] suiteName=$APP_GROUP_ID 으로 NSUserDefaults 생성 실패 — App Group capability 확인 필요")
    }
    return prefs
}

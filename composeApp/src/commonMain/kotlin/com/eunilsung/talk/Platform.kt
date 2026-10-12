package com.eunilsung.talk

interface Platform {
    /** [ANDROID] 또는 [IOS]. */
    val name: String
    val deviceId: String
    val deviceModel: String

    /** Android 기기인지. 시스템 알림 설정처럼 Android 에만 있는 항목을 가를 때 쓴다. */
    val isAndroid: Boolean get() = name == ANDROID

    companion object {
        const val ANDROID = "ANDROID"
        const val IOS = "IPHONE"
    }
}
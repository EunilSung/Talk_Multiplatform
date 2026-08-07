package com.eunilsung.talk.data.local

import kotlinx.coroutines.flow.StateFlow

/** 외부 URL 을 platform default 브라우저 / 스토어 앱으로 열기. [isDownloading] 은 APK 다운로드 진행 상태. */
interface ExternalUrlOpener {
    fun open(url: String)
    val isDownloading: StateFlow<Boolean>
}

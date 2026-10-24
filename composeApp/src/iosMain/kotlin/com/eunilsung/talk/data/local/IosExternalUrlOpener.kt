package com.eunilsung.talk.data.local

import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.util.Log
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS 외부 URL 열기.
 *  - [open] : 언제나 그대로 `openURL` 로 Safari 에 넘긴다. `.plist` 로 끝나도 설치 링크로 바꾸지 않는다.
 *  - [openUpdate] : 강제 업데이트 안내만 쓴다. https 이고 경로가 `.plist` 로 끝나면 `itms-services` 설치 링크로 바꿔 연다.
 *    `itms-apps://` 는 App Store 로, 그 밖의 http·https 는 Safari 로 넘긴다([updateUrlTarget]).
 */
class IosExternalUrlOpener : ExternalUrlOpener {

    override val isDownloading: StateFlow<Boolean> = MutableStateFlow(false)

    override fun open(url: String) {
        if (!isWebUrl(url)) return
        openNative(url.trim())
    }

    override fun openUpdate(url: String) {
        val trimmed = url.trim()
        when (updateUrlTarget(trimmed, UpdateRule.IOS)) {
            UpdateUrlTarget.INSTALL ->
                openNative("itms-services://?action=download-manifest&url=${trimmed.encodeURLParameter()}")
            UpdateUrlTarget.VIEW -> openNative(trimmed)
            UpdateUrlTarget.NONE -> Log.message("[UrlOpener/iOS] update url not opened url=$trimmed")
        }
    }

    private fun openNative(resolved: String) {
        Log.message("[UrlOpener/iOS] open url=$resolved")

        val nsUrl = NSURL.URLWithString(resolved) ?: run {
            Log.message("[UrlOpener/iOS] invalid url=$resolved")
            return
        }

        val canOpen = UIApplication.sharedApplication.canOpenURL(nsUrl)
        Log.message("[UrlOpener/iOS] canOpenURL=$canOpen")

        runCatching {
            UIApplication.sharedApplication.openURL(
                url = nsUrl,
                options = emptyMap<Any?, Any?>(),
                completionHandler = { success ->
                    Log.message("[UrlOpener/iOS] openURL completion success=$success")
                }
            )
        }.onFailure { Log.message("[UrlOpener/iOS] openURL failed: ${it.message}") }
    }
}

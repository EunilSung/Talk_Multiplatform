package com.eunilsung.talk.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.util.Log
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

class IosExternalUrlOpener : ExternalUrlOpener {

    override val isDownloading: StateFlow<Boolean> = MutableStateFlow(false)

    override fun open(url: String) {
        if (url.isBlank()) return

        val resolved = if (url.endsWith(".plist", ignoreCase = true)) {
            "itms-services://?action=download-manifest&url=$url"
        } else {
            url
        }
        Log.message("[UrlOpener/iOS] open url=$resolved (raw=$url)")

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

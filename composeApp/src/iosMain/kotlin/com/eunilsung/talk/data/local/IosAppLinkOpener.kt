package com.eunilsung.talk.data.local

import com.eunilsung.talk.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import kotlin.coroutines.resume

/**
 * iOS [AppLinkOpener] — `canOpenURL` 로 받을 앱이 있는지 보고 `openURL` 로 연다.
 *
 * `canOpenURL` 은 Info.plist 의 `LSApplicationQueriesSchemes` 에 적은 스킴만 답한다. 적지 않은 스킴은 앱이 있어도 false 다.
 */
class IosAppLinkOpener : AppLinkOpener {

    override suspend fun open(url: String): Boolean = withContext(Dispatchers.Main) {
        val nsUrl = NSURL.URLWithString(url) ?: return@withContext false
        val application = UIApplication.sharedApplication
        if (!application.canOpenURL(nsUrl)) {
            Log.message("[AppLinkOpener/iOS] no app for scheme=${nsUrl.scheme}")
            return@withContext false
        }
        suspendCancellableCoroutine { continuation ->
            application.openURL(
                url = nsUrl,
                options = emptyMap<Any?, Any?>(),
                completionHandler = { success -> if (continuation.isActive) continuation.resume(success) },
            )
        }
    }
}

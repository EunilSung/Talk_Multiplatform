package com.eunilsung.talk.data.remote.push

import kotlinx.coroutines.suspendCancellableCoroutine
import com.eunilsung.talk.domain.repository.SystemNotificationGate
import com.eunilsung.talk.util.Log
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDevice
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/** [SystemNotificationGate] iOS 구현. */
class IosSystemNotificationGate : SystemNotificationGate {

    override fun openSettings() {
        val systemVersion = UIDevice.currentDevice.systemVersion.toDoubleOrNull() ?: 0.0
        val urlString = if (systemVersion >= 16.0) {
            UIApplicationOpenNotificationSettingsURLString
        } else {
            UIApplicationOpenSettingsURLString
        }
        val url = NSURL.URLWithString(urlString) ?: run {
            Log.message("[SystemNotificationGate/iOS] invalid url: $urlString")
            return
        }
        UIApplication.sharedApplication.openURL(
            url = url,
            options = emptyMap<Any?, Any?>(),
            completionHandler = null
        )
    }

    override suspend fun isEnabled(): Boolean = suspendCancellableCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                val status = settings?.authorizationStatus
                val enabled = status == UNAuthorizationStatusAuthorized ||
                    status == UNAuthorizationStatusProvisional ||
                    status == UNAuthorizationStatusEphemeral
                cont.resume(enabled)
            }
    }
}

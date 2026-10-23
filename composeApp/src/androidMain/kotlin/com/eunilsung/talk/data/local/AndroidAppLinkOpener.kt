package com.eunilsung.talk.data.local

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.eunilsung.talk.util.Log

/**
 * Android [AppLinkOpener] — 링크를 `ACTION_VIEW` 로 넘겨 그 스킴을 받는 앱을 연다.
 *
 * 받을 앱이 없으면 `startActivity` 가 [ActivityNotFoundException] 을 던진다. 미리 찾아보지 않고 열어 본 결과로 판단해
 * 다른 앱 목록을 볼 권한(`<queries>`)이 필요 없다.
 */
class AndroidAppLinkOpener(private val context: Context) : AppLinkOpener {

    override suspend fun open(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            Log.message("[AppLinkOpener/Android] no app for scheme=${url.substringBefore(':')}")
            false
        }
    }
}

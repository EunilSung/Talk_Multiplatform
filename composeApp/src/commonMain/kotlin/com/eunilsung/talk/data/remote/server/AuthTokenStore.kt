package com.eunilsung.talk.data.remote.server

import com.eunilsung.talk.data.local.LocalSecret
import com.eunilsung.talk.data.repository.SettingsKeys
import com.russhwolf.settings.Settings

/**
 * 서버 인증 토큰 보관소.
 *
 * 서버는 요청에 적힌 아이디를 믿지 않는다 — 신원은 이 토큰이 정한다. 로그인할 때 한 번 받아 두고
 * 이후 모든 요청에 실어 보낸다.
 *
 * 값은 [Settings] 에 남아 앱을 껐다 켜도 유지되고, [LocalSecret] 으로 잠가 둔다 — 설정 파일이
 * 백업이나 기기 이전으로 새어 나가도 읽히지 않는다.
 */
class AuthTokenStore(private val settings: Settings) {

    private var cached: String? = null

    /** 없으면 빈 문자열 — 로그인 전이라는 뜻이다. */
    fun token(): String =
        cached ?: settings.getStringOrNull(SettingsKeys.KEY_AUTH_TOKEN)
            ?.let(LocalSecret::decrypt)
            .orEmpty()
            .also { cached = it }

    fun save(token: String) {
        cached = token
        settings.putString(SettingsKeys.KEY_AUTH_TOKEN, LocalSecret.encrypt(token))
    }

    fun clear() {
        cached = ""
        settings.remove(SettingsKeys.KEY_AUTH_TOKEN)
    }
}

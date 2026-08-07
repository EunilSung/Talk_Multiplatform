package com.eunilsung.talk.domain.repository

/** FCM 토큰 갱신 통보 책임. 토큰은 메모리에만 보관하고 영속 저장하지 않는다. */
interface PushTokenRepository {
    /** 플랫폼 SDK 가 새 토큰을 발급/갱신했을 때 호출. 같은 값이면 무시. */
    suspend fun updateToken(token: String)

    /** 로그인 성공 시 호출 — 토큰을 강제 재발급한다. */
    fun requestRefresh()
}

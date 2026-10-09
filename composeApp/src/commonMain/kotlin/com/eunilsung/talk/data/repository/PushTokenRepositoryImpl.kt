package com.eunilsung.talk.data.repository

import com.eunilsung.talk.Platform
import com.eunilsung.talk.data.remote.push.PushTokenBridge
import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.domain.repository.PushTokenRepository
import com.eunilsung.talk.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 이 기기의 푸시 토큰을 서버에 등록하는 저장소.
 *
 * 서버는 토큰을 로그인에 묶어 둔다 — 로그아웃하면 서버에서 함께 지워진다. 그래서 같은 토큰이어도
 * 로그인할 때마다 다시 등록해야 한다. 토큰은 메모리에만 두고 저장하지 않는다.
 */
class PushTokenRepositoryImpl(
    private val server: TalkServer,
    private val platform: Platform,
    private val loginRepository: LoginRepository,
) : PushTokenRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()

    private var latestToken: String? = null

    /** 지금 로그인에서 서버에 등록을 마친 토큰. 로그아웃하면 비운다. */
    private var registeredToken: String? = null

    init {
        repositoryScope.launch {
            loginRepository.isLoggedIn.collect { isLoggedIn ->
                if (isLoggedIn) {
                    requestRefresh()
                    register()
                } else {
                    mutex.withLock { registeredToken = null }
                }
            }
        }
    }

    override suspend fun updateToken(token: String) {
        if (token.isBlank()) return
        mutex.withLock { latestToken = token }
        register()
    }

    override fun requestRefresh() = PushTokenBridge.requestRefresh()

    /**
     * 받아 둔 토큰을 서버에 올린다. 로그인 전이거나 이미 올린 토큰이면 아무것도 하지 않는다.
     *
     * 실패하면 등록한 것으로 적지 않는다. 다음에 토큰이 다시 오거나 다시 로그인할 때 또 시도한다.
     */
    private suspend fun register() = mutex.withLock {
        val token = latestToken ?: return@withLock
        if (!loginRepository.isLoggedIn.value || token == registeredToken) return@withLock
        if (server.registerPushToken(token, platform.name) is ServerResult.Success) {
            registeredToken = token
            Log.message("[Push] token registered (${token.take(12)}…)")
        }
    }
}

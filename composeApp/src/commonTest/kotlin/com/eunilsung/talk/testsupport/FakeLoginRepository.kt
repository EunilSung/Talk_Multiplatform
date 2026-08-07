package com.eunilsung.talk.testsupport

import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.domain.repository.LoginRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 저장소가 init 에서 구독하는 로그인 상태만 흉내 낸다.
 * 기본값 false — 백그라운드 시드가 끼어들지 않아 테스트가 결정적으로 돈다.
 */
class FakeLoginRepository : LoginRepository {

    override val isLoggedIn = MutableStateFlow(false)
    override val isConnected = MutableStateFlow(false)
    override val isForeground = MutableStateFlow(true)

    override fun logout() {
        isLoggedIn.value = false
    }

    override suspend fun requestLogin(input: Login.LoginRequest): Login.LoginResult =
        Login.LoginResult.Success

    override suspend fun duplicateLogin(input: Login.LoginRequest): Login.LoginResult =
        Login.LoginResult.Success

    override fun getSavedId(): String? = null
    override fun getSavedPw(): String? = null
    override fun isSavePw(): Boolean = false
    override fun setSavePw(isSave: Boolean) = Unit
    override fun checkConnectionAndRelogin() = Unit
}

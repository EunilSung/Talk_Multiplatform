package com.eunilsung.talk.data.repository

import com.eunilsung.talk.Config
import com.eunilsung.talk.data.remote.server.AuthTokenStore
import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.testsupport.FakeTalkServer
import com.eunilsung.talk.testsupport.TestLocalSecret
import com.eunilsung.talk.testsupport.TestMyInfo
import com.eunilsung.talk.testsupport.awaitUntil
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 서버 로그인 저장소 — 서버 대역을 끼워 토큰·내 정보·저장값이 어떻게 바뀌는지 본다. */
class LoginRepositoryImplTest {

    private lateinit var settings: MapSettings
    private lateinit var server: FakeTalkServer
    private lateinit var tokenStore: AuthTokenStore
    private lateinit var repository: LoginRepositoryImpl

    @BeforeTest
    fun setUp() {
        TestLocalSecret.install()
        TestMyInfo.clear()
        settings = MapSettings()
        server = FakeTalkServer()
        tokenStore = AuthTokenStore(settings)
        repository = LoginRepositoryImpl(settings, server, tokenStore)
    }

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    @Test
    fun 로그인하면_토큰을_보관하고_내_정보를_채운다() = runTest {
        val result = repository.requestLogin(Login.LoginRequest("test1", FakeTalkServer.PASSWORD))

        assertIs<Login.LoginResult.Success>(result)
        assertTrue(repository.isLoggedIn.value)
        assertEquals("token-1", tokenStore.token())
        assertEquals("test1", Config.MyInfo.userId)
        assertEquals("김민준", Config.MyInfo.userName)
        assertEquals("test1", repository.getSavedId())
    }

    @Test
    fun 토큰은_설정에_평문으로_남지_않고_다시_켜도_읽힌다() = runTest {
        repository.requestLogin(Login.LoginRequest("test1", FakeTalkServer.PASSWORD))

        assertFalse(settings.getString(SettingsKeys.KEY_AUTH_TOKEN, "").contains("token-1"))
        assertEquals("token-1", AuthTokenStore(settings).token())
    }

    @Test
    fun 비밀번호가_틀리면_로그인되지_않고_토큰도_없다() = runTest {
        val result = repository.requestLogin(Login.LoginRequest("test1", "wrong"))

        assertIs<Login.LoginResult.Error>(result)
        assertFalse(repository.isLoggedIn.value)
        assertEquals("", tokenStore.token())
        assertEquals("", Config.MyInfo.userId)
    }

    @Test
    fun 서버에_닿지_못하면_로그인되지_않는다() = runTest {
        server.isReachable = false

        val result = repository.requestLogin(Login.LoginRequest("test1", FakeTalkServer.PASSWORD))

        assertIs<Login.LoginResult.Error>(result)
        assertFalse(repository.isLoggedIn.value)
    }

    @Test
    fun 비밀번호_저장을_켜면_잠가서_두고_끄면_지운다() = runTest {
        repository.setSavePw(true)
        repository.requestLogin(Login.LoginRequest("test1", FakeTalkServer.PASSWORD))

        assertEquals(FakeTalkServer.PASSWORD, repository.getSavedPw())
        assertFalse(settings.getString(SettingsKeys.KEY_PW, "") == FakeTalkServer.PASSWORD)

        repository.setSavePw(false)
        assertNull(repository.getSavedPw())
        assertFalse(settings.hasKey(SettingsKeys.KEY_PW))
    }

    @Test
    fun 로그아웃하면_토큰과_내_정보를_지우고_서버에서도_토큰을_없앤다() = runTest {
        repository.requestLogin(Login.LoginRequest("test1", FakeTalkServer.PASSWORD))

        repository.logout()

        assertFalse(repository.isLoggedIn.value)
        assertEquals("", tokenStore.token())
        assertEquals("", Config.MyInfo.userId)
        awaitUntil { "token-1" in server.revoked }
    }

    @Test
    fun 다시_로그인하면_이전_토큰을_서버에서_없앤다() = runTest {
        repository.requestLogin(Login.LoginRequest("test1", FakeTalkServer.PASSWORD))
        repository.requestLogin(Login.LoginRequest("test1", FakeTalkServer.PASSWORD))

        assertEquals("token-2", tokenStore.token())
        awaitUntil { "token-1" in server.revoked }
        assertFalse("token-2" in server.revoked)
    }

    @Test
    fun 서버가_토큰을_모른다고_하면_로그아웃된다() = runTest {
        repository.requestLogin(Login.LoginRequest("test1", FakeTalkServer.PASSWORD))
        server.isTokenValid = false

        repository.checkConnectionAndRelogin()

        awaitUntil { !repository.isLoggedIn.value }
        assertEquals("", tokenStore.token())
    }

    @Test
    fun 서버에_닿지_못한_것만으로는_로그아웃되지_않는다() = runTest {
        repository.requestLogin(Login.LoginRequest("test1", FakeTalkServer.PASSWORD))
        server.isReachable = false

        repository.checkConnectionAndRelogin()

        awaitUntil { !repository.isConnected.value }
        assertTrue(repository.isLoggedIn.value)
        assertEquals("token-1", tokenStore.token())
    }
}


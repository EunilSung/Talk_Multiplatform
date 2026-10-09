package com.eunilsung.talk.data.repository

import com.eunilsung.talk.Platform
import com.eunilsung.talk.testsupport.FakeLoginRepository
import com.eunilsung.talk.testsupport.FakeTalkServer
import com.eunilsung.talk.testsupport.awaitUntil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 푸시 토큰 저장소 — 토큰이 언제 서버에 올라가는지 본다. */
class PushTokenRepositoryImplTest {

    private object TestPlatform : Platform {
        override val name = "ANDROID"
        override val deviceId = "test-device"
        override val deviceModel = "test-model"
    }

    private lateinit var server: FakeTalkServer
    private lateinit var login: FakeLoginRepository
    private lateinit var repo: PushTokenRepositoryImpl

    @BeforeTest
    fun setUp() {
        server = FakeTalkServer()
        login = FakeLoginRepository()
        repo = PushTokenRepositoryImpl(server, TestPlatform, login)
    }

    private fun registrations(): List<String> = server.calls.filter { it.startsWith("registerPush") }

    @Test
    fun 로그인한_상태에서_토큰이_오면_플랫폼_이름과_함께_등록한다() = runTest {
        login.isLoggedIn.value = true
        settle()

        repo.updateToken("fcm-1")

        assertEquals(listOf("registerPush:fcm-1:ANDROID"), registrations())
    }

    @Test
    fun 로그인_전에_온_토큰은_로그인한_뒤에_등록한다() = runTest {
        repo.updateToken("fcm-1")
        assertTrue(registrations().isEmpty())

        login.isLoggedIn.value = true
        settle()

        awaitUntil { registrations().size == 1 }
    }

    @Test
    fun 같은_토큰이_다시_와도_한_번만_등록한다() = runTest {
        login.isLoggedIn.value = true
        settle()
        repo.updateToken("fcm-1")

        repo.updateToken("fcm-1")
        repo.updateToken(" ")

        assertEquals(1, registrations().size)
    }

    @Test
    fun 로그아웃했다_다시_로그인하면_같은_토큰도_다시_등록한다() = runTest {
        login.isLoggedIn.value = true
        settle()
        repo.updateToken("fcm-1")

        login.isLoggedIn.value = false
        settle()
        login.isLoggedIn.value = true
        settle()

        awaitUntil { registrations().size == 2 }
    }

    @Test
    fun 등록에_실패하면_다음_토큰이_올_때_다시_시도한다() = runTest {
        login.isLoggedIn.value = true
        settle()
        server.isReachable = false
        repo.updateToken("fcm-1")

        server.isReachable = true
        repo.updateToken("fcm-1")

        assertEquals(2, registrations().size)
    }

    /**
     * 저장소가 로그인 상태가 바뀐 것을 자기 스레드에서 알아챌 때까지 실제 시간으로 기다린다.
     * 기다리지 않으면 그 처리가 테스트의 다음 줄과 뒤섞여 등록 횟수가 실행마다 달라진다.
     */
    private suspend fun settle() = withContext(Dispatchers.Default) { delay(SETTLE_MS) }

    private companion object {
        const val SETTLE_MS = 200L
    }
}

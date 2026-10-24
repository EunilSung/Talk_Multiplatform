package com.eunilsung.talk.server.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class LoginAttemptLimiterTest {

    private var clock = 0L
    private val limiter = LoginAttemptLimiter(maxFailures = 3, lockMillis = 60_000, now = { clock })

    @Test
    fun `연달아 틀리면 잠기고 시간이 지나면 풀린다`() {
        repeat(2) { limiter.recordFailure("test1") }
        assertEquals(0, limiter.secondsUntilUnlocked("test1"))

        limiter.recordFailure("test1")
        assertEquals(60, limiter.secondsUntilUnlocked("test1"))

        clock = 59_001
        assertEquals(1, limiter.secondsUntilUnlocked("test1"))
        clock = 60_000
        assertEquals(0, limiter.secondsUntilUnlocked("test1"))
    }

    @Test
    fun `잠금이 풀린 뒤에는 처음부터 다시 센다`() {
        repeat(3) { limiter.recordFailure("test1") }
        clock = 60_000
        limiter.secondsUntilUnlocked("test1")

        repeat(2) { limiter.recordFailure("test1") }

        assertEquals(0, limiter.secondsUntilUnlocked("test1"))
    }

    @Test
    fun `맞게 로그인하면 틀린 횟수가 지워진다`() {
        repeat(2) { limiter.recordFailure("test1") }
        limiter.recordSuccess("test1")
        repeat(2) { limiter.recordFailure("test1") }

        assertEquals(0, limiter.secondsUntilUnlocked("test1"))
    }

    @Test
    fun `아이디마다 따로 세고 대소문자와 공백은 같은 아이디로 본다`() {
        limiter.recordFailure("test1")
        limiter.recordFailure(" TEST1 ")
        limiter.recordFailure("Test1")

        assertEquals(60, limiter.secondsUntilUnlocked("test1"))
        assertEquals(0, limiter.secondsUntilUnlocked("test2"))
    }

    @Test
    fun `한참 뒤의 실패는 처음부터 다시 센다`() {
        val windowed = LoginAttemptLimiter(maxFailures = 3, lockMillis = 60_000, now = { clock }, failureWindowMillis = 600_000)
        repeat(2) { windowed.recordFailure("test1") }

        clock = 600_000
        windowed.recordFailure("test1")

        assertEquals(0, windowed.secondsUntilUnlocked("test1"))
    }

    @Test
    fun `기록이 한도에 닿으면 가장 오래 조용했던 기록부터 버리고 잠긴 기록은 남긴다`() {
        val small = LoginAttemptLimiter(maxFailures = 2, lockMillis = 60_000, now = { clock }, maxRecords = 2)
        repeat(2) { small.recordFailure("victim") }
        clock = 1
        small.recordFailure("first")
        clock = 2
        small.recordFailure("second")
        clock = 3
        small.recordFailure("first")

        assertEquals(60, small.secondsUntilUnlocked("victim"))
        assertEquals(0, small.secondsUntilUnlocked("first"))
    }
}

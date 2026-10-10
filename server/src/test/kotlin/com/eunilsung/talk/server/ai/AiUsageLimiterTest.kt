package com.eunilsung.talk.server.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiUsageLimiterTest {

    private var clock = 0L
    private val limiter = AiUsageLimiter(perUserHourly = 2, globalDaily = 3, now = { clock })

    @Test
    fun `한 사람은 한 시간에 정해진 횟수까지만 쓰고 시간이 지나면 다시 쓴다`() {
        assertTrue(limiter.tryAcquire("test1"))
        assertTrue(limiter.tryAcquire("test1"))
        assertFalse(limiter.tryAcquire("test1"))

        clock = 60 * 60 * 1000L
        assertTrue(limiter.tryAcquire("test1"))
    }

    @Test
    fun `서버 전체의 하루 한도에 걸리면 다른 사람도 쓰지 못한다`() {
        assertTrue(limiter.tryAcquire("test1"))
        assertTrue(limiter.tryAcquire("test2"))
        assertTrue(limiter.tryAcquire("test3"))

        assertFalse(limiter.tryAcquire("test4"))

        clock = 24 * 60 * 60 * 1000L
        assertTrue(limiter.tryAcquire("test4"))
    }

    @Test
    fun `거절된 요청은 횟수에 넣지 않는다`() {
        repeat(2) { limiter.tryAcquire("test1") }
        repeat(5) { assertFalse(limiter.tryAcquire("test1")) }

        assertTrue(limiter.tryAcquire("test2"))
    }
}

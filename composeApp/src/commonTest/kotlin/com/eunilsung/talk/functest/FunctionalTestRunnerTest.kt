package com.eunilsung.talk.functest

import com.eunilsung.talk.testsupport.TestMyInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 러너 자체의 약속 — 서버 없이 돈다. 러너가 틀리면 그 위의 시나리오 결과를 전부 믿을 수 없다. */
class FunctionalTestRunnerTest {

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    @Test
    fun 테스트_계정이_아니면_아무것도_돌리지_않고_거부한다() = runTest {
        TestMyInfo.loginAs(userId = "real.user")
        var ran = false

        val passed = FunctionalTestRunner.run("거부", listOf(scenario("돌면 안 된다") { ran = true }))

        assertFalse(passed)
        assertFalse(ran)
    }

    @Test
    fun 로그인하지_않았으면_거부한다() = runTest {
        TestMyInfo.clear()

        assertFalse(FunctionalTestRunner.run("거부", listOf(scenario("돌면 안 된다") {})))
    }

    @Test
    fun 시나리오_하나가_깨져도_다음_시나리오는_돌고_전체는_실패다() = runTest {
        TestMyInfo.loginAs()
        val ran = mutableListOf<String>()

        val passed = FunctionalTestRunner.run(
            "계속",
            listOf(
                scenario("깨짐") {
                    step("깨지는 단계") { require(false, "일부러 깬다") }
                    step("돌면 안 되는 단계") { ran += "깨진 시나리오의 뒤 단계" }
                },
                scenario("통과") { step("도는 단계") { ran += "다음 시나리오" } },
            ),
        )

        assertFalse(passed)
        assertEquals(listOf("다음 시나리오"), ran)
    }

    @Test
    fun 단계_밖에서_난_오류도_실패로_센다() = runTest {
        TestMyInfo.loginAs()

        assertFalse(FunctionalTestRunner.run("오류", listOf(scenario("준비하다 죽음") { error("준비 실패") })))
    }

    @Test
    fun 건너뛴_단계는_실패가_아니고_뒤_단계는_계속_돈다() = runTest {
        TestMyInfo.loginAs()
        var after = false

        val passed = FunctionalTestRunner.run(
            "건너뜀",
            listOf(
                scenario("AI 없는 서버") {
                    step("건너뛰는 단계") { skip("AI 가 꺼져 있다") }
                    step("뒤 단계") { after = true }
                },
            ),
        )

        assertTrue(passed)
        assertTrue(after)
    }

    @Test
    fun 기다려도_되지_않으면_그_단계가_깨진다() = runTest {
        TestMyInfo.loginAs()

        val passed = FunctionalTestRunner.run(
            "기다림",
            listOf(scenario("오지 않는 알림") { step("기다린다") { awaitTrue("오지 않는다", timeoutMs = 100) { false } } }),
        )

        assertFalse(passed)
    }
}

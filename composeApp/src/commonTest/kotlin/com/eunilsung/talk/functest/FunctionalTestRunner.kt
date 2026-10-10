package com.eunilsung.talk.functest

import com.eunilsung.talk.Config

/**
 * 시나리오를 순서대로 돌리고 결과를 로그로 남긴다.
 *
 * ### 테스트 계정이 아니면 돌지 않는다
 *
 * 기능 테스트는 가짜 서버가 아니라 진짜 서버에 붙어 방을 만들고 대화를 보낸다. 로그인한 계정이
 * [ALLOWED_ACCOUNTS] 밖이면 **아무것도 실행하지 않고 거부한다.** 시나리오가 상대로 쓰는 계정도
 * 전부 이 안에 있다([RealServerHarness]).
 *
 * ### 하나가 깨져도 계속 돈다
 *
 * 시나리오가 실패해도 다음 시나리오로 넘어간다. 첫 실패에서 멈추면 한 번 돌릴 때마다 하나씩밖에
 * 못 고친다. 실패는 요약에 모아 마지막에 다시 보여 준다.
 */
object FunctionalTestRunner {

    /** 서버가 처음 뜰 때 만들어 두는 테스트 계정. 기능 테스트는 이 계정들끼리만 주고받는다. */
    val ALLOWED_ACCOUNTS: Set<String> = (1..10).map { "test$it" }.toSet()

    /**
     * [scenarios] 를 순서대로 실행한다.
     *
     * @return 전부 통과하면 true.
     */
    suspend fun run(suiteName: String, scenarios: List<Scenario>): Boolean {
        val me = Config.MyInfo.userId
        if (me !in ALLOWED_ACCOUNTS) {
            FT.line("ABORT suite=$suiteName reason=테스트 계정이 아니다 userId=$me")
            return false
        }

        FT.line("SUITE-START name=$suiteName total=${scenarios.size} account=$me")
        val startedAt = nowMillis()
        val failures = mutableListOf<String>()
        var skipped = 0

        scenarios.forEach { scenario ->
            val scope = ScenarioScope(scenario.name)
            val scenarioStartedAt = nowMillis()
            FT.line("SCENARIO-START name=${scenario.name}")
            try {
                scenario.body(scope)
            } catch (_: StepFailure) {
                /** 단계 로그에 이미 사유가 남았다. 여기서는 다음 시나리오로 넘어가기만 한다. */
            } catch (error: Throwable) {
                FT.line("SCENARIO-ERROR name=${scenario.name} reason=${error::class.simpleName} ${error.message}")
                failures += scenario.name
            }
            if (scope.failed && scenario.name !in failures) failures += scenario.name
            skipped += scope.skippedSteps
            val result = if (scenario.name in failures) "FAIL" else "PASS"
            FT.line("SCENARIO-END name=${scenario.name} result=$result ms=${nowMillis() - scenarioStartedAt}")
        }

        val passed = scenarios.size - failures.size
        FT.line(
            "SUITE-END name=$suiteName passed=$passed failed=${failures.size} skippedSteps=$skipped " +
                "ms=${nowMillis() - startedAt}" + if (failures.isEmpty()) "" else " failures=$failures"
        )
        return failures.isEmpty()
    }
}

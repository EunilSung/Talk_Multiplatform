package com.eunilsung.talk.functest

import com.eunilsung.talk.domain.model.Login
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue

/**
 * 시나리오 묶음 하나를 진짜 서버에 돌린다.
 *
 * 서버 주소가 없으면 아무것도 하지 않고 지나간다. 있으면 테스트 계정으로 로그인하고, 시나리오를 전부
 * 돌린 뒤, 만든 방을 정리하고, 하나라도 깨졌으면 테스트를 실패시킨다.
 *
 * 가상 시간(`runTest`)으로 돌리지 않는다. 서버의 응답과 알림은 실제 시간에 오고, 가상 시간에서는
 * 요청의 시간 제한이 응답보다 먼저 터진다.
 */
fun functionalSuite(suiteName: String, scenarios: (RealServerHarness) -> List<Scenario>) = runBlocking {
    val harness = RealServerHarness.startOrNull() ?: return@runBlocking
    try {
        val signedIn = harness.signIn()
        assertTrue(signedIn is Login.LoginResult.Success, "$suiteName — 테스트 계정으로 로그인하지 못했다: $signedIn")
        val passed = FunctionalTestRunner.run(suiteName, scenarios(harness))
        assertTrue(passed, "$suiteName — 깨진 시나리오가 있다. ${FT.TAG} 로그의 STEP-FAIL 을 볼 것")
    } finally {
        harness.close()
    }
}

/** 지난 실행과 겹치지 않는 이름표. 방 이름·그룹 이름·대화 내용에 붙여 "이번에 만든 것"을 가려낸다. */
fun runTag(): String = "${RealServerHarness.TAG_PREFIX}${nowMillis()}"

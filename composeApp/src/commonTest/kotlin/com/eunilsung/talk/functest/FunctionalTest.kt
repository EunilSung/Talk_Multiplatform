package com.eunilsung.talk.functest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/**
 * 기능 테스트의 기록 창구.
 *
 * 한 줄에 사건 하나, `[FT]` 로 시작한다. 실 서버에 붙어 도는 테스트는 실패했을 때 "어느 단계에서
 * 무엇이 달랐는지"가 로그에 남아 있어야 다시 돌리지 않고 원인을 짚을 수 있다.
 */
object FT {
    const val TAG = "[FT]"

    fun line(text: String) {
        println("$TAG $text")
    }
}

/** 단계가 깨졌다. 사유는 이미 로그에 남았고, 이 예외는 시나리오의 남은 단계를 건너뛰게 할 뿐이다. */
class StepFailure(message: String) : AssertionError(message)

/** 이 단계는 지금 서버에서 확인할 수 없다 — 예를 들어 AI 키가 없는 서버. 실패가 아니다. */
class StepSkipped(message: String) : RuntimeException(message)

/**
 * 시나리오 하나가 도는 동안의 문맥. 단계마다 이름을 붙여 통과·실패·건너뜀을 남긴다.
 *
 * 한 단계가 깨지면 그 시나리오의 뒤 단계는 돌지 않는다 — 앞 단계가 만든 상태에 기대고 있기 때문이다.
 * 다른 시나리오는 계속 돈다([FunctionalTestRunner]).
 */
class ScenarioScope(val scenarioName: String) {

    var failed = false
        private set

    var skippedSteps = 0
        private set

    suspend fun step(name: String, body: suspend () -> Unit) {
        val startedAt = nowMillis()
        try {
            body()
            FT.line("STEP-PASS scenario=$scenarioName step=$name ms=${nowMillis() - startedAt}")
        } catch (skipped: StepSkipped) {
            skippedSteps++
            FT.line("STEP-SKIP scenario=$scenarioName step=$name reason=${skipped.message}")
        } catch (failure: StepFailure) {
            failed = true
            FT.line("STEP-FAIL scenario=$scenarioName step=$name reason=${failure.message}")
            throw failure
        } catch (error: Throwable) {
            failed = true
            FT.line("STEP-FAIL scenario=$scenarioName step=$name reason=${error::class.simpleName} ${error.message}")
            throw StepFailure(error.message.orEmpty())
        }
    }

    fun require(condition: Boolean, reason: String) {
        if (!condition) throw StepFailure(reason)
    }

    fun requireEquals(expected: Any?, actual: Any?, what: String) {
        if (expected != actual) throw StepFailure("$what — 기대=$expected 실제=$actual")
    }

    fun skip(reason: String): Nothing = throw StepSkipped(reason)

    /**
     * [condition] 이 참이 될 때까지 실제 시간으로 기다린다. 서버의 알림과 저장소의 반영은 요청이 끝난
     * 뒤에 따로 도착한다. 시간 안에 안 되면 [what] 을 사유로 단계를 깬다.
     */
    suspend fun awaitTrue(what: String, timeoutMs: Long = AWAIT_TIMEOUT_MS, condition: suspend () -> Boolean) {
        val reached = withContext(Dispatchers.Default) {
            withTimeoutOrNull(timeoutMs) {
                while (!condition()) delay(AWAIT_POLL_MS)
                true
            }
        }
        if (reached != true) throw StepFailure("기다렸지만 되지 않았다: $what (${timeoutMs}ms)")
    }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 8_000L
        const val AWAIT_POLL_MS = 50L
    }
}

class Scenario(val name: String, val body: suspend ScenarioScope.() -> Unit)

fun scenario(name: String, body: suspend ScenarioScope.() -> Unit) = Scenario(name, body)

internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

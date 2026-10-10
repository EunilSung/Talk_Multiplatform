package com.eunilsung.talk.functest

import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.shared.api.ChatErrorCode
import kotlinx.coroutines.delay

/**
 * AI 가 만든 결과를 얻는다. 얻지 못하면 까닭에 따라 건너뛰거나 깬다.
 *
 * 모델 제공처는 잠깐씩 요청을 거절한다(분당 한도). 한 번 거절됐다고 깨면 서버와 앱이 멀쩡해도
 * 테스트가 오락가락하므로 몇 번 다시 묻는다. 그래도 안 되면 [probe] 로 서버에 직접 물어,
 * "AI 를 쓸 수 없다"는 답이면 건너뛰고(꺼진 서버·한도), 서버는 되는데 [ask] 만 안 되는 것이면 깬다.
 */
suspend fun <T : Any> ScenarioScope.aiResult(probe: suspend () -> ServerResult<*>, ask: suspend () -> T?): T {
    repeat(AI_ATTEMPTS) { attempt ->
        ask()?.let { return it }
        if (attempt < AI_ATTEMPTS - 1) delay(AI_RETRY_WAIT_MS)
    }
    val result = probe()
    if (result is ServerResult.Rejected && result.code == ChatErrorCode.AI_UNAVAILABLE) {
        skip("서버의 AI 가 꺼져 있거나 한도에 걸렸다")
    }
    throw StepFailure("서버는 AI 결과를 주는데 저장소가 주지 않았다: $result")
}

private const val AI_ATTEMPTS = 3
private const val AI_RETRY_WAIT_MS = 4_000L

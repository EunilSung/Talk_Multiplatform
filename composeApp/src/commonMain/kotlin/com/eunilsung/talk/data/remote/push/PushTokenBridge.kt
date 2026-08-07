package com.eunilsung.talk.data.remote.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.repository.PushTokenRepository
import com.eunilsung.talk.util.Log
import org.koin.mp.KoinPlatform
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** 플랫폼 FCM SDK 와 공통 로직 사이의 브릿지 — 토큰 수신/강제 재발급 진입점. */
object PushTokenBridge {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** 연속 [requestRefresh] 호출 시 중복 재발급을 막는 최소 간격. */
    private val REFRESH_MIN_INTERVAL = 10.seconds

    private var refreshAction: (() -> Unit)? = null
    private var lastRefreshMark: TimeSource.Monotonic.ValueTimeMark? = null

    /** 플랫폼이 강제 토큰 재발급 로직을 등록. */
    fun setRefreshAction(action: () -> Unit) {
        refreshAction = action
    }

    /** 강제 토큰 재발급 요청. [REFRESH_MIN_INTERVAL] 쿨다운으로 연속 호출은 1회만 수행. */
    fun requestRefresh() {
        val action = refreshAction
        if (action == null) {
            Log.message("[Push] refresh requested but no platform action registered")
            return
        }
        val recent = lastRefreshMark?.elapsedNow()?.let { it < REFRESH_MIN_INTERVAL } ?: false
        if (recent) {
            Log.message("[Push] token refresh skipped — throttled")
            return
        }
        lastRefreshMark = TimeSource.Monotonic.markNow()
        Log.message("[Push] requesting token refresh")
        runCatching { action() }
            .onFailure { Log.message("[Push] refresh action failed: ${it.message}") }
    }

    fun onTokenReceived(token: String) {
        if (token.isBlank()) return
        val repository = runCatching {
            KoinPlatform.getKoin().get<PushTokenRepository>()
        }.getOrNull() ?: return

        scope.launch {
            runCatching { repository.updateToken(token) }
        }
    }
}

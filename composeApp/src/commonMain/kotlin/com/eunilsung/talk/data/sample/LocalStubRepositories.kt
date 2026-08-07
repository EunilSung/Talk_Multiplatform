package com.eunilsung.talk.data.sample

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.eunilsung.talk.domain.repository.MainRepository
import com.eunilsung.talk.domain.repository.PushTokenRepository
import com.eunilsung.talk.domain.model.MainEvent
import com.eunilsung.talk.util.Log

/** 로컬 스텁 저장소 모음. */

/** FCM 토큰 등록 — 로그만 남긴다. */
class LocalPushTokenRepositoryImpl : PushTokenRepository {
    override suspend fun updateToken(token: String) {
        Log.message("[PushToken/Local] token received (${token.take(12)}…) — 서버 미연동, 전송 생략")
    }

    override fun requestRefresh() {
        Log.message("[PushToken/Local] refresh requested — 서버 미연동, 무시")
    }
}

/** 메인 이벤트(강제 로그아웃) 로컬 대체 — [emit] 으로 수동 발생 가능. */
class LocalMainRepositoryImpl : MainRepository {
    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 4)
    override val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    fun emit(event: MainEvent) {
        _events.tryEmit(event)
    }
}

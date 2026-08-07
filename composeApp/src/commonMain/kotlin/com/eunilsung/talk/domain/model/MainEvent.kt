package com.eunilsung.talk.domain.model

/** 앱 전역 일회성 이벤트 — [com.eunilsung.talk.domain.repository.MainRepository] 가 방출한다. */
sealed class MainEvent {
    data class ForcedLogout(val message: String) : MainEvent()
}

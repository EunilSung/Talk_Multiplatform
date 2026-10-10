package com.eunilsung.talk.domain.model

/** 안읽은 대화를 요약해 달라고 했을 때의 결과. */
sealed interface ChatSummary {
    data class Ready(val text: String) : ChatSummary

    /** 요약할 대화가 없다. */
    data object Empty : ChatSummary

    /** 지금은 요약할 수 없다 — 서버에 닿지 못했거나 AI 가 꺼져 있거나 한도에 걸렸다. */
    data object Unavailable : ChatSummary
}

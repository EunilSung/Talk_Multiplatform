package com.eunilsung.talk.domain.model

/**
 * 푸시 라우팅 요청.
 *
 * @param kind "TALK" / "MSG" / "BADGE" 등 (대문자 정규화 후)
 * @param msgKey 대화방 ID
 */
data class PushNavRequest(
    val kind: String,
    val msgKey: String,
    val categoryId: String,
)

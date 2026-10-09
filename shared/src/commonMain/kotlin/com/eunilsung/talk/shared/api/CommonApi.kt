package com.eunilsung.talk.shared.api

import kotlinx.serialization.Serializable

/** 실패 응답 공통 모양. [code] 로 앱이 문구를 고르고, [message] 는 로그용이다. */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
)

/** `/health` 응답. DB 연결과 마이그레이션이 끝난 뒤에만 [status] 가 `ok` 로 나간다. */
@Serializable
data class HealthResponse(
    val status: String,
) {
    companion object {
        const val OK = "ok"
    }
}

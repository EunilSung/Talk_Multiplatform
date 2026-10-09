package com.eunilsung.talk.data.remote.server

import com.eunilsung.talk.Config
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.LoginRequest
import com.eunilsung.talk.shared.api.LoginResponse
import com.eunilsung.talk.shared.api.UserDto
import com.eunilsung.talk.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * 서버 요청 한 번의 결과.
 *
 * 서버가 거절한 것([Rejected])과 서버에 닿지 못한 것([Unreachable])을 가른다. 앞쪽은 다시 보내도
 * 같은 답이 오고, 뒤쪽은 다시 보내 볼 수 있다. 섞이면 화면이 잘못된 안내를 하게 된다.
 */
sealed interface ServerResult<out T> {
    data class Success<T>(val value: T) : ServerResult<T>

    /** 서버가 답했지만 거절했다. [code] 는 `ApiErrorCode` 값이다. */
    data class Rejected(val status: Int, val code: String) : ServerResult<Nothing>

    /** 응답이 없거나 읽을 수 없었다 — 네트워크 끊김·타임아웃·서버 다운. */
    data object Unreachable : ServerResult<Nothing>
}

/**
 * Talk 서버와 주고받는 것들.
 *
 * 인터페이스로 둔 이유는 테스트다. 실제 호출을 대신할 대역을 끼울 수 있어야 저장소 로직을
 * 네트워크 없이 검증할 수 있다.
 */
interface TalkServer {
    suspend fun login(id: String, password: String): ServerResult<LoginResponse>

    /** [token] 을 서버에서 지운다. 실패해도 앱은 로그아웃된 것으로 본다. */
    suspend fun logout(token: String)

    /** 지금 토큰의 주인. 토큰이 아직 유효한지 확인하는 데도 쓴다. */
    suspend fun me(): ServerResult<UserDto>
}

class TalkServerClient(
    private val httpClient: HttpClient,
    private val tokenStore: AuthTokenStore,
    private val baseUrl: String = Config.Server.BASE_URL,
) : TalkServer {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun login(id: String, password: String): ServerResult<LoginResponse> =
        request("로그인", LoginResponse.serializer()) {
            httpClient.post("$baseUrl/auth/login") {
                jsonBody(LoginRequest.serializer(), LoginRequest(id, password))
            }
        }

    override suspend fun logout(token: String) {
        if (token.isBlank()) return
        send("로그아웃") {
            httpClient.post("$baseUrl/auth/logout") { header(HttpHeaders.Authorization, "Bearer $token") }
        }
    }

    override suspend fun me(): ServerResult<UserDto> =
        request("내 정보", UserDto.serializer()) {
            httpClient.get("$baseUrl/users/me") { auth() }
        }

    /**
     * 인증 헤더.
     *
     * 공용 [httpClient] 에 기본 헤더로 박지 않는다. 그 클라이언트는 이미지 로딩과 링크 미리보기도
     * 함께 쓰므로, 박아 두면 남의 서버에까지 우리 토큰이 실려 나간다.
     */
    private fun HttpRequestBuilder.auth() {
        val token = tokenStore.token()
        if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun <T> HttpRequestBuilder.jsonBody(serializer: KSerializer<T>, body: T) {
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(serializer, body))
    }

    /** 본문이 있는 응답을 읽는다. */
    private suspend fun <T> request(
        label: String,
        serializer: KSerializer<T>,
        call: suspend () -> HttpResponse,
    ): ServerResult<T> {
        val (response, text) = send(label, call) ?: return ServerResult.Unreachable
        if (!response.status.isSuccess()) return rejected(label, response, text)
        val value = runCatching { json.decodeFromString(serializer, text) }.getOrNull()
        if (value == null) {
            Log.message("[Server] $label 응답을 읽지 못했다")
            return ServerResult.Unreachable
        }
        return ServerResult.Success(value)
    }

    /** 요청을 보내고 상태와 본문을 받는다. 닿지 못하면 null. */
    private suspend fun send(label: String, call: suspend () -> HttpResponse): Pair<HttpResponse, String>? =
        runCatching {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                val response = call()
                response to response.bodyAsText()
            }
        }.getOrElse {
            Log.message("[Server] $label 실패 — ${it.message}")
            null
        } ?: run {
            Log.message("[Server] $label 응답 없음 — $baseUrl")
            null
        }

    private fun rejected(label: String, response: HttpResponse, text: String): ServerResult.Rejected {
        val code = runCatching { json.decodeFromString(ApiError.serializer(), text).code }.getOrDefault("")
        Log.message("[Server] $label 거절 — ${response.status.value} $code")
        return ServerResult.Rejected(response.status.value, code)
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000L
    }
}

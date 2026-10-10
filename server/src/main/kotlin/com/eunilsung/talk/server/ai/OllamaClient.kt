package com.eunilsung.talk.server.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * 서버 옆에서 도는 로컬 모델 호출 (Ollama).
 *
 * 대화가 서버 밖으로 나가지 않는다. 규칙은 system 역할로, 자료와 요청은 user 역할로 따로 보낸다.
 * 답을 한 번에 받게 `stream` 을 끈다. 모델을 처음 메모리에 올릴 때는 오래 걸려서 넉넉히 기다린다.
 */
class OllamaClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val model: String,
) : AiClient {

    private val log = LoggerFactory.getLogger(OllamaClient::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun ask(system: String, input: String): AiReply {
        val body = json.encodeToString(
            Request.serializer(),
            Request(model = model, messages = listOf(Message(ROLE_SYSTEM, system), Message(ROLE_USER, input))),
        )
        val response = runCatching {
            withTimeoutOrNull(TIMEOUT_MS) {
                val response = httpClient.post("${baseUrl.trimEnd('/')}$CHAT_PATH") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                response.status.value to response.bodyAsText()
            }
        }.getOrElse {
            log.warn("로컬 모델 호출 실패 — {}", it.message)
            null
        } ?: return AiReply.Failed

        val (status, text) = response
        if (status !in 200..299) {
            log.warn("로컬 모델 호출 거부 status={} body={}", status, text.take(200))
            return AiReply.Failed
        }
        val answer = runCatching { json.decodeFromString(Response.serializer(), text) }
            .onFailure { log.warn("로컬 모델 응답을 읽지 못했다 — {}", it.message) }
            .getOrNull()
            ?.message?.content?.trim()
        return if (answer.isNullOrEmpty()) AiReply.Failed else AiReply.Answer(answer)
    }

    @Serializable
    private data class Request(
        val model: String,
        val messages: List<Message>,
        val stream: Boolean = false,
        val options: Options = Options(),
    )

    @Serializable
    private data class Message(val role: String = "", val content: String = "")

    @Serializable
    private data class Options(@SerialName("num_predict") val maxOutputTokens: Int = MAX_OUTPUT_TOKENS)

    @Serializable
    private data class Response(val message: Message? = null)

    companion object {
        /** 모델을 처음 올리는 시간까지 기다린다. HTTP 엔진의 읽기 제한도 이 값에 맞춘다. */
        const val TIMEOUT_MS = 60_000L

        private const val CHAT_PATH = "/api/chat"
        private const val ROLE_SYSTEM = "system"
        private const val ROLE_USER = "user"
        /** 메신저 말풍선 하나에 들어갈 길이면 충분하다. */
        private const val MAX_OUTPUT_TOKENS = 800
    }
}

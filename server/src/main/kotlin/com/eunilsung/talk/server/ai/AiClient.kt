package com.eunilsung.talk.server.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.header
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

/** 모델에 한 번 물은 결과. 한도에 걸린 것을 따로 알아야 사용자에게 "잠시 후"라고 말할 수 있다. */
sealed interface AiReply {
    data class Answer(val text: String) : AiReply

    /** 제공처의 사용량 한도에 걸렸다. 잠시 뒤에는 될 수 있다. */
    data object Busy : AiReply

    /** 그 밖의 실패 — 응답이 없거나 읽을 수 없었다. */
    data object Failed : AiReply
}

/**
 * 언어 모델에 묻는 통로. 테스트에서는 대역을 끼운다.
 *
 * [system] 은 모델이 따를 규칙이고 [input] 은 그 규칙 아래에서 다룰 자료와 요청이다.
 * 둘을 섞어 보내지 않는다 — 자료(대화 내용) 안의 문장이 규칙처럼 읽히면 안 된다.
 */
fun interface AiClient {
    suspend fun ask(system: String, input: String): AiReply
}

/**
 * Google Gemini 호출 (Interactions API).
 *
 * 대화를 서버에 남기지 않게 `store` 를 끄고, 한 번 묻고 한 번 받는다. 생각하는 단계는 최소로 둔다 —
 * 메신저 답변은 빨라야 하고, 무료 한도에서는 토큰을 아껴야 한다.
 */
class GeminiClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val model: String,
) : AiClient {

    private val log = LoggerFactory.getLogger(GeminiClient::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun ask(system: String, input: String): AiReply {
        val body = json.encodeToString(Request.serializer(), Request(model = model, input = input, systemInstruction = system))
        val response = runCatching {
            withTimeoutOrNull(TIMEOUT_MS) {
                val response = httpClient.post(ENDPOINT) {
                    header(API_KEY_HEADER, apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                response.status.value to response.bodyAsText()
            }
        }.getOrElse {
            log.warn("AI 호출 실패 — {}", it.message)
            null
        } ?: return AiReply.Failed

        val (status, text) = response
        if (status == STATUS_TOO_MANY_REQUESTS) {
            log.warn("AI 사용량 한도 도달")
            return AiReply.Busy
        }
        if (status !in 200..299) {
            log.warn("AI 호출 거부 status={} body={}", status, text.take(200))
            return AiReply.Failed
        }
        val answer = runCatching { json.decodeFromString(Response.serializer(), text) }.getOrNull()
            ?.steps.orEmpty()
            .filter { it.type == STEP_MODEL_OUTPUT }
            .flatMap { it.content }
            .filter { it.type == CONTENT_TEXT }
            .joinToString("") { it.text }
            .trim()
        return if (answer.isNullOrEmpty()) AiReply.Failed else AiReply.Answer(answer)
    }

    @Serializable
    private data class Request(
        val model: String,
        val input: String,
        @SerialName("system_instruction") val systemInstruction: String,
        val store: Boolean = false,
        @SerialName("generation_config") val generationConfig: GenerationConfig = GenerationConfig(),
    )

    @Serializable
    private data class GenerationConfig(
        @SerialName("max_output_tokens") val maxOutputTokens: Int = MAX_OUTPUT_TOKENS,
        @SerialName("thinking_level") val thinkingLevel: String = THINKING_LEVEL,
    )

    @Serializable
    private data class Response(val steps: List<Step> = emptyList())

    @Serializable
    private data class Step(val type: String = "", val content: List<Content> = emptyList())

    @Serializable
    private data class Content(val type: String = "", val text: String = "")

    private companion object {
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
        const val API_KEY_HEADER = "x-goog-api-key"
        const val STEP_MODEL_OUTPUT = "model_output"
        const val CONTENT_TEXT = "text"
        const val THINKING_LEVEL = "minimal"
        /** 메신저 말풍선 하나에 들어갈 길이면 충분하다. */
        const val MAX_OUTPUT_TOKENS = 800
        const val TIMEOUT_MS = 30_000L
        const val STATUS_TOO_MANY_REQUESTS = 429
    }
}

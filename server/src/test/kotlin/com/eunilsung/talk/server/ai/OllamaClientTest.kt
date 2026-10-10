package com.eunilsung.talk.server.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 로컬 모델 호출 — 규칙과 자료가 따로 나가는지, 답과 실패를 제대로 읽는지 본다.
 *
 * 가상 시간(`runTest`)으로 돌리면 응답을 기다리는 사이 시간 제한이 먼저 터진다. 실제 시간으로 돌린다.
 */
class OllamaClientTest {

    private var requestedUrl = ""
    private var requestedBody = ""

    private fun client(status: HttpStatusCode, body: String): OllamaClient {
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            requestedBody = String(request.body.toByteArray())
            respond(body, status)
        }
        return OllamaClient(HttpClient(engine), "http://localhost:11434/", "gemma3:4b")
    }

    @Test
    fun 규칙은_system으로_자료는_user로_나누어_한_번에_답을_받게_묻는다() = runBlocking {
        val ollama = client(HttpStatusCode.OK, """{"message":{"role":"assistant","content":" 3시입니다 \n"},"done":true}""")

        val reply = ollama.ask("규칙", "자료")

        assertEquals(AiReply.Answer("3시입니다"), reply)
        assertEquals("http://localhost:11434/api/chat", requestedUrl)
        val sent = Json.parseToJsonElement(requestedBody).jsonObject
        assertEquals("gemma3:4b", sent.getValue("model").jsonPrimitive.content)
        assertEquals(false, sent.getValue("stream").jsonPrimitive.boolean)
        val messages = sent.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(listOf("system" to "규칙", "user" to "자료"), messages.map {
            it.getValue("role").jsonPrimitive.content to it.getValue("content").jsonPrimitive.content
        })
    }

    @Test
    fun 거절당하거나_답이_비었거나_읽을_수_없으면_실패다() = runBlocking {
        assertEquals(AiReply.Failed, client(HttpStatusCode.NotFound, """{"error":"model not found"}""").ask("규칙", "자료"))
        assertEquals(AiReply.Failed, client(HttpStatusCode.OK, """{"message":{"content":"  "}}""").ask("규칙", "자료"))
        assertEquals(AiReply.Failed, client(HttpStatusCode.OK, "not json").ask("규칙", "자료"))
    }
}

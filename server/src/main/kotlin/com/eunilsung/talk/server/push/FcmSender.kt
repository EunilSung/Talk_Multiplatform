package com.eunilsung.talk.server.push

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory

/** 한 건 발송 결과. 토큰을 지워야 하는 실패를 따로 알아야 해서 나눈다. */
enum class PushResult {
    SENT,
    /** "그런 토큰 없다"는 답 — 앱을 지웠거나 토큰이 바뀌었다. 저장된 토큰을 버려야 한다. */
    UNREGISTERED,
    /** 그 밖의 실패. 일시적일 수 있어 토큰은 그대로 둔다. */
    FAILED,
}

/** 기기 한 대로 푸시를 보내는 통로. 테스트에서는 대역을 끼운다. */
fun interface PushSender {
    suspend fun send(deviceToken: String, data: Map<String, String>): PushResult
}

/**
 * FCM HTTP v1 발송.
 *
 * data 메시지만 보낸다. notification 메시지를 섞으면 앱이 백그라운드일 때 안드로이드가 알림을 직접
 * 그려 버려 앱의 수신 코드가 불리지 않는다. 이 앱은 "보고 있는 방이면 알림을 띄우지 않는다" 같은
 * 판단을 앱이 해야 해서, 표시 여부를 OS 에 맡길 수 없다.
 */
class FcmSender(
    private val projectId: String,
    private val tokens: GoogleAccessTokenProvider,
    private val httpClient: HttpClient,
) : PushSender {
    private val log = LoggerFactory.getLogger(FcmSender::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun send(deviceToken: String, data: Map<String, String>): PushResult {
        val accessToken = tokens.token() ?: return PushResult.FAILED

        val response = runCatching {
            httpClient.post("https://fcm.googleapis.com/v1/projects/$projectId/messages:send") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), bodyOf(deviceToken, data)))
            }
        }.getOrElse {
            log.warn("푸시 발송 실패 — {}", it.message)
            return PushResult.FAILED
        }

        if (response.status.value in 200..299) return PushResult.SENT

        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        if (isGoneToken(response.status.value, text)) {
            log.info("푸시 토큰 폐기 — status={} token={}…", response.status, deviceToken.take(12))
            return PushResult.UNREGISTERED
        }
        log.warn("푸시 발송 거부 status={} body={}", response.status, text.take(200))
        return PushResult.FAILED
    }

    companion object {
        private val errorJson = Json { ignoreUnknownKeys = true }

        /**
         * 이 토큰으로는 다시 보내도 같은 답이 오는지 — 그러면 토큰을 버린다.
         *
         * 없는 토큰(404·`UNREGISTERED`·`InvalidRegistration`)과 다른 프로젝트의 토큰(403 `SENDER_ID_MISMATCH`)은 버린다.
         * 400 `INVALID_ARGUMENT` 는 토큰이 틀렸을 때만 버린다 — 오류 상세의 `fieldViolations` 가 `message.token` 을 가리키거나
         * "유효한 등록 토큰이 아니다"라는 문구가 있을 때다. 본문이 너무 크거나 data 칸이 틀려도 같은 400 이 오는데, 그때
         * 토큰을 버리면 대화 한 건으로 받는 사람 모든 기기의 정상 토큰이 지워진다.
         */
        fun isGoneToken(status: Int, body: String): Boolean =
            status == 404 || "UNREGISTERED" in body || "InvalidRegistration" in body ||
                (status == 400 && isTokenRejected(body)) ||
                (status == 403 && "SENDER_ID_MISMATCH" in body)

        /** 400 오류가 토큰 칸을 탓하는지. 읽을 수 없는 본문이면 토큰 탓으로 보지 않는다(버리지 않는 쪽이 안전하다). */
        private fun isTokenRejected(body: String): Boolean {
            val error = runCatching { errorJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
                ?: return false
            val message = (error["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val violatedFields = (error["details"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.get("fieldViolations") as? JsonArray }
                .flatten()
                .mapNotNull { ((it as? JsonObject)?.get("field") as? JsonPrimitive)?.contentOrNull }
            return TOKEN_FIELD in violatedFields || message.contains(INVALID_TOKEN_MESSAGE, ignoreCase = true)
        }

        /** FCM 이 토큰 칸을 가리킬 때 쓰는 이름. */
        private const val TOKEN_FIELD = "message.token"

        /** 토큰이 틀렸을 때 FCM 이 주는 문구("The registration token is not a valid FCM registration token")에 든 말. */
        private const val INVALID_TOKEN_MESSAGE = "registration token is not a valid"
    }

    /**
     * 발송 본문. 대화 알림은 늦게 오면 의미가 없어 절전 모드에서도 바로 깨우게 하고(우선순위 높음),
     * iOS 는 data 만 있는 알림이 `content-available` 이 있어야 앱을 깨운다.
     */
    private fun bodyOf(deviceToken: String, data: Map<String, String>) = JsonObject(
        mapOf(
            "message" to JsonObject(
                mapOf(
                    "token" to JsonPrimitive(deviceToken),
                    "data" to JsonObject(data.mapValues { JsonPrimitive(it.value) }),
                    "android" to JsonObject(mapOf("priority" to JsonPrimitive("high"))),
                    "apns" to JsonObject(
                        mapOf(
                            "headers" to JsonObject(mapOf("apns-priority" to JsonPrimitive("10"))),
                            "payload" to JsonObject(
                                mapOf("aps" to JsonObject(mapOf("content-available" to JsonPrimitive(1)))),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}

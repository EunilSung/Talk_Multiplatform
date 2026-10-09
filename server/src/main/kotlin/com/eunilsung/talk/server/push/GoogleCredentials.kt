package com.eunilsung.talk.server.push

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * 서비스 계정 키 파일.
 *
 * FCM 은 이 계정으로 서명한 JWT 를 액세스 토큰으로 바꿔야 부를 수 있다. `firebase-admin` 을
 * 쓰면 이 파일을 안 만들어도 되지만, 그 SDK 는 gRPC 와 자체 Netty 를 끌고 온다 — 이 서버가
 * Ktor Netty 위에 있어 버전이 부딪힐 자리를 만들 이유가 없다. 필요한 것은 JWT 서명 하나뿐이고
 * 그건 JDK 에 이미 있다.
 */
@Serializable
data class ServiceAccount(
    @SerialName("project_id") val projectId: String,
    @SerialName("client_email") val clientEmail: String,
    @SerialName("private_key") val privateKey: String,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): ServiceAccount? =
            runCatching { json.decodeFromString(serializer(), raw) }.getOrNull()
                ?.takeIf { it.projectId.isNotBlank() && it.clientEmail.isNotBlank() }
    }
}

/**
 * 서비스 계정으로 구글 액세스 토큰을 받아 온다.
 *
 * 토큰은 한 시간짜리라 매번 받으면 푸시 한 건에 왕복이 둘이 된다. 만료 1 분 전까지 재사용하고,
 * 여러 요청이 동시에 만료를 만나도 한 번만 받도록 잠근다.
 */
class GoogleAccessTokenProvider(
    private val account: ServiceAccount,
    private val httpClient: HttpClient,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val log = LoggerFactory.getLogger(GoogleAccessTokenProvider::class.java)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private var cached: String? = null
    private var expiresAtSeconds: Long = 0

    suspend fun token(): String? = mutex.withLock {
        val now = nowSeconds()
        cached?.takeIf { now < expiresAtSeconds - RENEW_MARGIN_SECONDS }?.let { return it }

        val assertion = runCatching { signedAssertion(now) }.getOrElse {
            log.error("JWT 서명 실패 — 서비스 계정 키를 읽을 수 없다: {}", it.message)
            return null
        }

        val response = runCatching {
            httpClient.submitForm(
                url = TOKEN_URL,
                formParameters = Parameters.build {
                    append("grant_type", GRANT_TYPE)
                    append("assertion", assertion)
                },
            )
        }.getOrElse {
            log.warn("액세스 토큰 요청 실패 — {}", it.message)
            return null
        }

        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        if (!response.status.value.let { it in 200..299 }) {
            log.warn("액세스 토큰 거부 status={} body={}", response.status, body.take(200))
            return null
        }
        val parsed = runCatching { json.decodeFromString(TokenResponse.serializer(), body) }.getOrNull()
            ?: run {
                log.warn("액세스 토큰 응답 해석 실패")
                return null
            }

        cached = parsed.accessToken
        expiresAtSeconds = now + parsed.expiresIn
        return parsed.accessToken
    }

    /** RS256 으로 서명한 JWT. 헤더·페이로드·서명을 각각 base64url 로 이어 붙인다. */
    private fun signedAssertion(now: Long): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = """
            {"iss":"${account.clientEmail}","scope":"$SCOPE","aud":"$TOKEN_URL",
             "iat":$now,"exp":${now + ASSERTION_LIFETIME_SECONDS}}
        """.trimIndent().replace("\n", "").replace(" ", "")

        val unsigned = "${header.base64Url()}.${claims.base64Url()}"
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(readPrivateKey())
            update(unsigned.toByteArray())
        }.sign()
        return "$unsigned.${signature.base64Url()}"
    }

    private fun readPrivateKey(): java.security.PrivateKey {
        val der = account.privateKey
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .filterNot { it.isWhitespace() }
        val bytes = Base64.getDecoder().decode(der)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long = 3600,
    )

    private companion object {
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        const val ASSERTION_LIFETIME_SECONDS = 3600L
        /** 만료 직전에 쓰다 401 을 맞지 않도록 미리 갈아 끼우는 여유. */
        const val RENEW_MARGIN_SECONDS = 60L
    }
}

private fun String.base64Url(): String = toByteArray().base64Url()

private fun ByteArray.base64Url(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(this)

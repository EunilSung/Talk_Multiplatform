package com.eunilsung.talk.server

import com.eunilsung.talk.server.ai.AiAssistant
import com.eunilsung.talk.server.ai.AiClient
import com.eunilsung.talk.server.ai.AiUsageLimiter
import com.eunilsung.talk.server.ai.GeminiClient
import com.eunilsung.talk.server.auth.LoginAttemptLimiter
import com.eunilsung.talk.server.routes.aiRoutes
import com.eunilsung.talk.server.config.ServerConfig
import com.eunilsung.talk.server.db.Database
import com.eunilsung.talk.server.chat.ChatHub
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.files.FileStorage
import com.eunilsung.talk.server.repository.ChatRepository
import com.eunilsung.talk.server.repository.FileRepository
import com.eunilsung.talk.server.routes.fileRoutes
import com.eunilsung.talk.server.repository.VoteRepository
import com.eunilsung.talk.server.routes.voteRoutes
import com.eunilsung.talk.server.repository.ChatGroupRepository
import com.eunilsung.talk.server.routes.chatGroupRoutes
import com.eunilsung.talk.server.repository.ContactGroupRepository
import com.eunilsung.talk.server.routes.peopleRoutes
import com.eunilsung.talk.server.push.ChatPushService
import com.eunilsung.talk.server.push.FcmSender
import com.eunilsung.talk.server.push.GoogleAccessTokenProvider
import com.eunilsung.talk.server.push.PushSender
import com.eunilsung.talk.server.push.ServiceAccount
import com.eunilsung.talk.server.repository.PushTokenRepository
import com.eunilsung.talk.server.routes.pushRoutes
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import com.eunilsung.talk.server.routes.chatRoutes
import com.eunilsung.talk.server.repository.UserRepository
import com.eunilsung.talk.server.routes.authRoutes
import com.eunilsung.talk.server.routes.healthRoutes
import com.eunilsung.talk.server.seed.SeedAccounts
import com.eunilsung.talk.shared.api.ApiError
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(Netty, port = config.port) { module(config) }.start(wait = true)
}

/** 운영·로컬 진입점 — 설정대로 DB 에 붙고 마이그레이션을 돌린 뒤 [module] 을 올린다. */
fun Application.module(config: ServerConfig) {
    val log = LoggerFactory.getLogger("Application")
    log.info(LocalAddresses.describe(config.port))
    module(
        dataSource = Database.connect(config.db),
        fileStorage = FileStorage(File(config.filesDirectory)),
        pushSender = fcmSenderOf(config.firebaseCredentialsJson),
        aiClient = aiClientOf(config.aiApiKey, config.aiModel),
    )
}

/**
 * 서비스 계정 키로 푸시 발송기를 만든다. 키가 없거나 읽을 수 없으면 null — 푸시만 꺼지고 서버는 뜬다.
 * 키 하나 때문에 서버 전체가 안 뜨면 로컬 개발이 매번 막힌다.
 */
private fun fcmSenderOf(credentialsJson: String): PushSender? {
    val log = LoggerFactory.getLogger("Application")
    if (credentialsJson.isBlank()) {
        log.warn("FIREBASE_CREDENTIALS 없음 — 푸시가 나가지 않는다")
        return null
    }
    val account = ServiceAccount.parse(credentialsJson)
    if (account == null) {
        log.warn("서비스 계정 키를 해석하지 못했다 — 푸시가 나가지 않는다")
        return null
    }
    log.info("푸시 사용 — project={}", account.projectId)
    val httpClient = HttpClient(OkHttp)
    return FcmSender(account.projectId, GoogleAccessTokenProvider(account, httpClient), httpClient)
}

/** API 키로 AI 호출 통로를 만든다. 키가 없으면 null — AI 만 꺼지고 서버는 뜬다. */
private fun aiClientOf(apiKey: String, model: String): AiClient? {
    val log = LoggerFactory.getLogger("Application")
    if (apiKey.isBlank()) {
        log.warn("GEMINI_API_KEY 없음 — AI 가 답하지 않는다")
        return null
    }
    log.info("AI 사용 — model={}", model)
    return GeminiClient(HttpClient(OkHttp), apiKey, model)
}

/**
 * 서버 본체. 이미 준비된 [dataSource] 와 [fileStorage] 를 받는다 — 테스트가 자기 DB 와 임시 폴더를 끼워 넣는 자리다.
 * [pushSender] 가 null 이면 푸시를 보내지 않고, [aiClient] 가 null 이면 AI 가 "설정되지 않았다"고만 답한다.
 */
fun Application.module(
    dataSource: DataSource,
    fileStorage: FileStorage,
    pushSender: PushSender? = null,
    aiClient: AiClient? = null,
) {
    val log = LoggerFactory.getLogger("Application")

    install(ContentNegotiation) { json(ServerJson) }
    install(CallLogging) { level = Level.INFO }
    install(WebSockets) {
        pingPeriod = WEBSOCKET_PING_SECONDS.seconds
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("처리하지 못한 예외", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error", "일시적인 오류입니다"))
        }
    }

    val users = UserRepository(dataSource)
    val tokens = AuthTokenRepository(dataSource)
    val chats = ChatRepository(dataSource)
    val pushTokens = PushTokenRepository(dataSource)
    val chatHub = ChatHub(ChatPushService(pushTokens, pushSender, this))
    val files = FileRepository(dataSource)
    SeedAccounts.ensure(users)
    AiAssistant.ensureAccount(users)
    val assistant = AiAssistant(chats, chatHub, aiClient, AiUsageLimiter(), this)
    chatHub.onNewMessage(assistant::onNewMessage)

    routing {
        healthRoutes(dataSource)
        authRoutes(users, tokens, LoginAttemptLimiter())
        chatRoutes(chats, files, fileStorage, tokens, chatHub)
        fileRoutes(files, fileStorage, tokens)
        voteRoutes(VoteRepository(chats), chats, tokens, chatHub)
        chatGroupRoutes(ChatGroupRepository(dataSource), tokens)
        peopleRoutes(users, ContactGroupRepository(dataSource), tokens, chatHub)
        pushRoutes(pushTokens, tokens)
        aiRoutes(assistant, chats, tokens)
    }
}

/** 앱과 같은 규칙으로 읽고 쓴다 — 모르는 필드는 넘기고 기본값도 실어 보낸다. */
val ServerJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** 중간 장비가 조용한 연결을 끊기 전에 살아 있음을 알리는 간격. */
private const val WEBSOCKET_PING_SECONDS = 15

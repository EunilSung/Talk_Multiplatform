package com.eunilsung.talk.server

import com.eunilsung.talk.server.ai.AiAssistant
import com.eunilsung.talk.server.ai.AiClient
import com.eunilsung.talk.server.ai.AiUsageLimiter
import com.eunilsung.talk.server.ai.GeminiClient
import com.eunilsung.talk.server.auth.LoginAttemptLimiter
import com.eunilsung.talk.server.config.ServerConfig
import com.eunilsung.talk.server.db.Database
import com.eunilsung.talk.server.chat.ChatHub
import com.eunilsung.talk.server.limits.RequestBodyLimit
import com.eunilsung.talk.server.limits.ServerLimits
import com.eunilsung.talk.server.limits.isFileUpload
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
import com.eunilsung.talk.server.routes.aiRoutes
import com.eunilsung.talk.server.routes.authRoutes
import com.eunilsung.talk.server.routes.healthRoutes
import com.eunilsung.talk.server.seed.SeedAccounts
import com.eunilsung.talk.shared.api.ApiError
import com.eunilsung.talk.shared.api.ApiErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds

fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = config.host) { module(config) }.start(wait = true)
}

/** 운영·로컬 진입점 — 설정대로 DB 에 붙고 마이그레이션을 돌린 뒤 [module] 을 올린다. */
fun Application.module(config: ServerConfig) {
    val log = LoggerFactory.getLogger("Application")
    log.info(LocalAddresses.describe(config.host, config.port))
    module(
        dataSource = Database.connect(config.db),
        fileStorage = FileStorage(File(config.filesDirectory)),
        pushSender = fcmSenderOf(config.firebaseCredentialsJson),
        aiClient = aiClientOf(config.aiApiKey, config.aiModel),
        aiUsageLimiter = config.aiPerUserHourly?.let { AiUsageLimiter(perUserHourly = it) } ?: AiUsageLimiter(),
        seedPassword = config.seedPassword,
        limits = config.limits,
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
    val httpClient = HttpClient(OkHttp) {
        engine { config { readTimeout(AI_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS) } }
    }
    return GeminiClient(httpClient, apiKey, model)
}

/**
 * 서버 본체. 이미 준비된 [dataSource] 와 [fileStorage] 를 받는다 — 테스트가 자기 DB 와 임시 폴더를 끼워 넣는 자리다.
 * [pushSender] 가 null 이면 푸시를 보내지 않고, [aiClient] 가 null 이면 AI 가 "설정되지 않았다"고만 답한다.
 * [seedPassword] 가 있을 때만 시연용 계정을 그 비밀번호로 맞춘다. 없으면 계정을 만들지 않는다.
 */
fun Application.module(
    dataSource: DataSource,
    fileStorage: FileStorage,
    pushSender: PushSender? = null,
    aiClient: AiClient? = null,
    aiUsageLimiter: AiUsageLimiter = AiUsageLimiter(),
    seedPassword: String? = null,
    limits: ServerLimits = ServerLimits(),
) {
    val log = LoggerFactory.getLogger("Application")

    install(ContentNegotiation) { json(ServerJson) }
    install(CallLogging) { level = Level.INFO }
    /** 파일 올리기 말고는 모두 작은 JSON 이다. 파일 올리기는 그 경로가 크기를 직접 센다. */
    install(RequestBodyLimit) {
        limitOf = { call -> if (call.isFileUpload()) null else limits.jsonBodyBytes }
    }
    /**
     * 앱은 이 연결로 프레임을 보내지 않는다. 프레임 크기를 작게 묶어, 큰 프레임 하나로 서버 힙을 채우지 못하게 한다
     * (기본값은 제한이 없다). 응답(pong)이 [WEBSOCKET_TIMEOUT_SECONDS] 안에 오지 않는 연결은 끊는다.
     */
    install(WebSockets) {
        pingPeriod = WEBSOCKET_PING_SECONDS.seconds
        timeout = WEBSOCKET_TIMEOUT_SECONDS.seconds
        maxFrameSize = WEBSOCKET_MAX_FRAME_BYTES
    }
    /**
     * 처리하지 못한 예외는 500 으로 답한다. 메모리 부족 같은 [Error] 는 잡지 않는다 — 잡아서 답하면 고장 난 프로세스가
     * 살아 있는 것처럼 남는다. 그런 오류는 프로세스가 끝나고 컨테이너가 다시 띄우게 둔다.
     */
    install(StatusPages) {
        exception<PayloadTooLargeException> { call, _ ->
            call.respond(HttpStatusCode.PayloadTooLarge, ApiError(ApiErrorCode.PAYLOAD_TOO_LARGE, "요청이 너무 큽니다"))
        }
        exception<Exception> { call, cause ->
            log.error("처리하지 못한 예외", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error", "일시적인 오류입니다"))
        }
    }

    val users = UserRepository(dataSource)
    val tokens = AuthTokenRepository(dataSource)
    val chats = ChatRepository(dataSource, limits.maxRoomMembers)
    val pushTokens = PushTokenRepository(dataSource)
    val chatHub = ChatHub(ChatPushService(pushTokens, pushSender, this), maxPerUser = limits.webSocketsPerUser)
    val files = FileRepository(dataSource, limits)
    seedAccounts(users, seedPassword)
    AiAssistant.ensureAccount(users)
    val assistant = AiAssistant(chats, chatHub, aiClient, aiUsageLimiter, this)
    chatHub.onNewMessage(assistant::onNewMessage)

    routing {
        healthRoutes(dataSource)
        authRoutes(users, tokens, LoginAttemptLimiter(), chatHub)
        chatRoutes(chats, files, fileStorage, tokens, chatHub, limits)
        fileRoutes(files, fileStorage, tokens, limits)
        voteRoutes(VoteRepository(chats), chats, tokens, chatHub, limits)
        chatGroupRoutes(ChatGroupRepository(dataSource), tokens)
        peopleRoutes(users, ContactGroupRepository(dataSource), tokens, chatHub)
        pushRoutes(pushTokens, tokens, limits.pushTokensPerUser)
        aiRoutes(assistant, tokens)
    }
}

/**
 * 시연용 계정을 [password] 가 있을 때만 맞춘다. 없으면 만들지 않고, 예전에 만든 계정이 남아 있으면 알린다 —
 * 그 계정은 예전 비밀번호 그대로라 `SEED_PASSWORD` 로 바꾸거나 지워야 한다.
 */
private fun seedAccounts(users: UserRepository, password: String?) {
    val log = LoggerFactory.getLogger("Application")
    if (password != null) {
        SeedAccounts.ensure(users, password)
        log.info("시연용 계정 사용 — test1~test10 의 비밀번호를 설정값으로 맞췄다")
        return
    }
    log.info("시연용 계정을 만들지 않는다 — SEED_DEMO_ACCOUNTS=true 와 SEED_PASSWORD 가 함께 있어야 만든다")
    if (SeedAccounts.anyExists(users)) {
        log.warn("시연용 계정이 남아 있다 — SEED_DEMO_ACCOUNTS=true 와 새 SEED_PASSWORD 로 다시 띄우면 비밀번호가 바뀐다")
    }
}

/** 앱과 같은 규칙으로 읽고 쓴다 — 모르는 필드는 넘기고 기본값도 실어 보낸다. */
val ServerJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** 중간 장비가 조용한 연결을 끊기 전에 살아 있음을 알리는 간격. */
private const val WEBSOCKET_PING_SECONDS = 15

/** ping 을 보낸 뒤 응답을 기다리는 시간. */
private const val WEBSOCKET_TIMEOUT_SECONDS = 15

/** 받는 프레임의 최대 바이트. 앱은 프레임을 보내지 않고, 제어 프레임(pong·close)은 이보다 훨씬 작다. */
private const val WEBSOCKET_MAX_FRAME_BYTES = 4096L

/**
 * 모델의 답을 기다리는 시간. OkHttp 의 기본값(10초)은 대화 맥락을 함께 보내는 방 안 답변에 모자라
 * [GeminiClient] 가 정한 전체 제한(30초)보다 먼저 끊긴다. 그 제한에 맞춘다.
 */
private const val AI_READ_TIMEOUT_SECONDS = 30L

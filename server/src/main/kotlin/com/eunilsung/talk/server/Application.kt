package com.eunilsung.talk.server

import com.eunilsung.talk.server.config.ServerConfig
import com.eunilsung.talk.server.db.Database
import com.eunilsung.talk.server.chat.ChatHub
import com.eunilsung.talk.server.repository.AuthTokenRepository
import com.eunilsung.talk.server.files.FileStorage
import com.eunilsung.talk.server.repository.ChatRepository
import com.eunilsung.talk.server.repository.FileRepository
import com.eunilsung.talk.server.routes.fileRoutes
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
    LoggerFactory.getLogger("Application").info(LocalAddresses.describe(config.port))
    module(Database.connect(config.db), FileStorage(File(config.filesDirectory)))
}

/**
 * 서버 본체. 이미 준비된 [dataSource] 와 [fileStorage] 를 받는다 — 테스트가 자기 DB 와 임시 폴더를 끼워 넣는 자리다.
 */
fun Application.module(dataSource: DataSource, fileStorage: FileStorage) {
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
    val chatHub = ChatHub()
    val files = FileRepository(dataSource)
    SeedAccounts.ensure(users)

    routing {
        healthRoutes(dataSource)
        authRoutes(users, tokens)
        chatRoutes(chats, files, fileStorage, tokens, chatHub)
        fileRoutes(files, fileStorage, tokens)
    }
}

/** 앱과 같은 규칙으로 읽고 쓴다 — 모르는 필드는 넘기고 기본값도 실어 보낸다. */
val ServerJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** 중간 장비가 조용한 연결을 끊기 전에 살아 있음을 알리는 간격. */
private const val WEBSOCKET_PING_SECONDS = 15

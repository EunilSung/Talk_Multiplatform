package com.eunilsung.talk.server.local

import com.eunilsung.talk.server.LocalAddresses
import com.eunilsung.talk.server.config.ServerConfig
import com.eunilsung.talk.server.limits.ServerLimits
import com.eunilsung.talk.server.module
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File

/**
 * 로컬 개발용 진입점 — `./gradlew :server:runLocal`.
 *
 * 내장 PostgreSQL 을 먼저 띄우고 그 주소로 서버를 올린다. 운영 진입점([com.eunilsung.talk.server.main])과
 * 다른 점은 셋이다 — DB 를 스스로 마련하고, 시연용 계정(test1~test10)을 알려진 비밀번호([LOCAL_SEED_PASSWORD])로 만들고,
 * 이 PC 안(127.0.0.1)에서만 받는다.
 *
 * 같은 Wi-Fi 의 다른 기기에 열지 않는다. 시연용 계정과 비밀번호가 공개돼 있어, 열어 두면 같은 망의 누구나 로그인해
 * 대화·파일·AI 를 쓸 수 있다. Android 에뮬레이터(10.0.2.2)와 iOS 시뮬레이터(localhost)는 이 상태로 붙는다.
 * 실기기는 `adb reverse tcp:8090 tcp:8090` 으로 붙이고, 꼭 같은 망으로 붙여야 하면 `HOST=0.0.0.0` 으로 띄운다.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: LOCAL_SERVER_PORT
    val host = localServerHost(System.getenv("HOST"))
    val postgres = LocalPostgres.startPersistent(File(LOCAL_DB_DIRECTORY), LOCAL_DB_PORT)
    Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })

    val config = ServerConfig(port = port, host = host, db = LocalPostgres.configOf(postgres), filesDirectory = LOCAL_FILES_DIRECTORY,
        firebaseCredentialsJson = ServerConfig.firebaseCredentialsFromEnv(),
        aiApiKey = ServerConfig.aiApiKeyFromEnv(),
        aiModel = ServerConfig.aiModelFromEnv(),
        aiPerUserHourly = ServerConfig.aiPerUserHourlyFromEnv(),
        seedPassword = System.getenv("SEED_PASSWORD")?.takeIf { it.isNotBlank() } ?: LOCAL_SEED_PASSWORD,
        limits = localServerLimits(host),
    )
    println("로컬 DB — ${config.db.url} (user=${config.db.user}, 비밀번호 없음)")
    if (!LocalAddresses.isLoopback(host)) {
        println("경고 — 같은 망에 열었다($host). 시연용 계정의 비밀번호가 공개돼 있어 같은 망의 누구나 로그인할 수 있다. 한도는 운영 기본값을 쓴다")
    }
    embeddedServer(Netty, port = port, host = host) { module(config) }.start(wait = true)
}

/** 로컬 서버가 받을 주소. `HOST` 가 비어 있으면 이 PC 안에서만 받는다. */
internal fun localServerHost(env: String?): String = env?.trim()?.takeIf { it.isNotEmpty() } ?: LOOPBACK_HOST

/**
 * 로컬 서버의 한도. 이 PC 안에서만 받을 때만 넉넉한 [LOCAL_LIMITS] 를 쓴다. 같은 망에 열면(`HOST=0.0.0.0`) 공개된 시연용
 * 비밀번호로 누구나 들어올 수 있으므로 운영 기본값으로 돌아간다.
 */
internal fun localServerLimits(host: String): ServerLimits =
    if (LocalAddresses.isLoopback(host)) LOCAL_LIMITS else ServerLimits()

/**
 * 로컬 개발·서버 테스트·기능 테스트·실화면 흐름이 쓰는 시연용 계정 비밀번호.
 *
 * 테스트 코드에만 있다. 운영 진입점에는 기본 비밀번호가 없고, 배포한 서버는 `SEED_PASSWORD` 로 따로 정한다.
 */
const val LOCAL_SEED_PASSWORD = "1234"

/**
 * 이 PC 안에서만 받는 로컬 서버의 한도. 기능 테스트와 실화면 흐름은 짧은 시간에 같은 계정으로 방과 파일을 많이 만든다.
 * 운영 한도에 걸려 검증이 깨지지 않게 넉넉히 준다. 루프백에만 열 때 쓰므로 남이 이 한도를 쓸 수 없다([localServerLimits]).
 */
internal val LOCAL_LIMITS = ServerLimits(
    fileUploadsPerDay = 10_000,
    fileBytesPerDay = 20L * 1024 * 1024 * 1024,
    concurrentUploadsPerUser = 20,
    roomCreatesPerMinute = 1_000,
    roomCreatesPerDay = 100_000,
    invitesPerMinute = 1_000,
    invitesPerDay = 100_000,
    voteCreatesPerMinute = 1_000,
    voteCreatesPerDay = 100_000,
    webSocketsPerUser = 20,
)

/** 운영 기본 포트(8080)를 피한다. 같은 PC 에서 다른 프로젝트의 서버가 그 포트를 쓰면 서로의 앱이 엉뚱한 서버에 붙는다. */
private const val LOCAL_SERVER_PORT = 8090
private const val LOOPBACK_HOST = "127.0.0.1"
private const val LOCAL_DB_DIRECTORY = ".localdb"
private const val LOCAL_FILES_DIRECTORY = ".localfiles"
private const val LOCAL_DB_PORT = 54329

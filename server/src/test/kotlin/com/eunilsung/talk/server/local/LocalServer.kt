package com.eunilsung.talk.server.local

import com.eunilsung.talk.server.config.ServerConfig
import com.eunilsung.talk.server.module
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File

/**
 * 로컬 개발용 진입점 — `./gradlew :server:runLocal`.
 *
 * 내장 PostgreSQL 을 먼저 띄우고 그 주소로 서버를 올린다. 운영 진입점([com.eunilsung.talk.server.main])과
 * 다른 점은 DB 를 스스로 마련한다는 것 하나다.
 */
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: LOCAL_SERVER_PORT
    val postgres = LocalPostgres.startPersistent(File(LOCAL_DB_DIRECTORY), LOCAL_DB_PORT)
    Runtime.getRuntime().addShutdownHook(Thread { postgres.close() })

    val config = ServerConfig(port = port, db = LocalPostgres.configOf(postgres), filesDirectory = LOCAL_FILES_DIRECTORY)
    println("로컬 DB — ${config.db.url} (user=${config.db.user}, 비밀번호 없음)")
    embeddedServer(Netty, port = port) { module(config) }.start(wait = true)
}

/** 운영 기본 포트(8080)를 피한다. 같은 PC 에서 다른 프로젝트의 서버가 그 포트를 쓰면 서로의 앱이 엉뚱한 서버에 붙는다. */
private const val LOCAL_SERVER_PORT = 8090
private const val LOCAL_DB_DIRECTORY = ".localdb"
private const val LOCAL_FILES_DIRECTORY = ".localfiles"
private const val LOCAL_DB_PORT = 54329

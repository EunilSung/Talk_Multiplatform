package com.eunilsung.talk.functest

import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.testsupport.createTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
actual fun functionalTestServerUrl(): String? = getenv("TALK_FUNCTEST_URL")?.toKString()?.takeIf { it.isNotBlank() }

actual fun functionalTestHttpClient(): HttpClient = HttpClient(Darwin) { install(WebSockets) }

/** native 드라이버는 메모리 DB 도 동시에 쓸 수 있다. 다른 테스트와 같은 것을 쓴다. */
actual fun createFunctionalTestDatabase(): AppDatabase = createTestDatabase()

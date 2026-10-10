package com.eunilsung.talk.functest

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
actual fun functionalTestServerUrl(): String? = getenv("TALK_FUNCTEST_URL")?.toKString()?.takeIf { it.isNotBlank() }

actual fun functionalTestHttpClient(): HttpClient = HttpClient(Darwin) { install(WebSockets) }

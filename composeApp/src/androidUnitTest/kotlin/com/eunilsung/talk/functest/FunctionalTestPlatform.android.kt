package com.eunilsung.talk.functest

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets

actual fun functionalTestServerUrl(): String? = System.getenv("TALK_FUNCTEST_URL")?.takeIf { it.isNotBlank() }

actual fun functionalTestHttpClient(): HttpClient = HttpClient(OkHttp) { install(WebSockets) }

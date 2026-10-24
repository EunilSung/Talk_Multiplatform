package com.eunilsung.talk.data.remote.linkpreview

import com.eunilsung.talk.domain.model.LinkPreviewState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 링크 미리보기 받기 — 내부망·평문 주소로는 요청을 보내지 않고, 리다이렉트도 주소마다 다시 보며, 본문은 앞부분만 읽는다.
 */
class LinkPreviewRepositoryImplTest {

    private val requested = mutableListOf<String>()

    private fun repository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = LinkPreviewRepositoryImpl(
        HttpClient(MockEngine { request ->
            requested += request.url.toString()
            this.handler(request)
        })
    )

    private suspend fun LinkPreviewRepositoryImpl.settled(url: String): LinkPreviewState =
        withContext(Dispatchers.Default) {
            withTimeout(WAIT_MS) { getState(url).first { it != LinkPreviewState.Loading } }
        }

    private fun MockRequestHandleScope.html(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))

    private fun MockRequestHandleScope.redirect(location: String) =
        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, location))

    @Test
    fun 공개된_https_주소의_og_메타를_읽는다() = runTest {
        val repo = repository { html(ogPage(title = "제목")) }

        val state = repo.settled("https://www.example.com/a")

        assertEquals("제목", assertIs<LinkPreviewState.Success>(state).data.title)
        assertEquals(listOf("https://www.example.com/a"), requested)
    }

    @Test
    fun 평문_http_와_사설_주소는_요청을_보내지_않는다() = runTest {
        val repo = repository { html(ogPage(title = "제목")) }

        listOf("http://www.example.com/a", "https://192.168.0.1/", "https://localhost:8090/").forEach {
            assertEquals(LinkPreviewState.Failure, repo.settled(it), it)
        }
        assertEquals(emptyList(), requested)
    }

    @Test
    fun 사설_주소로_넘기는_리다이렉트는_따라가지_않는다() = runTest {
        val repo = repository { request ->
            if (request.url.host == "www.example.com") redirect("https://10.0.0.1/admin") else html(ogPage(title = "내부"))
        }

        assertEquals(LinkPreviewState.Failure, repo.settled("https://www.example.com/a"))
        assertEquals(listOf("https://www.example.com/a"), requested)
    }

    @Test
    fun 공개_주소로_넘기는_리다이렉트는_따라가고_상대_경로도_푼다() = runTest {
        val repo = repository { request ->
            when (request.url.encodedPath) {
                "/a" -> redirect("/b")
                else -> html(ogPage(title = "옮긴 곳"))
            }
        }

        val state = repo.settled("https://www.example.com/a")

        assertEquals("옮긴 곳", assertIs<LinkPreviewState.Success>(state).data.title)
        assertEquals(listOf("https://www.example.com/a", "https://www.example.com/b"), requested)
    }

    @Test
    fun 리다이렉트가_너무_길면_그만둔다() = runTest {
        var hop = 0
        val repo = repository { redirect("https://www.example.com/hop${++hop}") }

        assertEquals(LinkPreviewState.Failure, repo.settled("https://www.example.com/start"))
        assertEquals(LinkPreviewRepositoryImpl.MAX_REDIRECTS + 1, requested.size)
    }

    @Test
    fun 본문은_상한까지만_읽는다() = runTest {
        val filler = "a".repeat(LinkPreviewRepositoryImpl.MAX_HTML_BYTES.toInt())
        val repo = repository { request ->
            if (request.url.encodedPath == "/late") html("<html><head>$filler${ogMeta("뒤")}</head></html>")
            else html(ogPage(title = "앞") + filler + filler)
        }

        assertEquals(LinkPreviewState.Failure, repo.settled("https://www.example.com/late"))
        assertEquals("앞", assertIs<LinkPreviewState.Success>(repo.settled("https://www.example.com/early")).data.title)
    }

    @Test
    fun 내부망_og_image_는_카드에_싣지_않는다() = runTest {
        val repo = repository {
            html(ogPage(title = "제목", image = "https://192.168.0.1/x.png"))
        }

        val state = assertIs<LinkPreviewState.Success>(repo.settled("https://www.example.com/a"))

        assertEquals("", state.data.imageUrl)
    }

    private fun ogMeta(title: String) = """<meta property="og:title" content="$title">"""

    private fun ogPage(title: String, image: String = "") = buildString {
        append("<html><head>")
        append(ogMeta(title))
        if (image.isNotEmpty()) append("""<meta property="og:image" content="$image">""")
        append("</head><body></body></html>")
    }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}

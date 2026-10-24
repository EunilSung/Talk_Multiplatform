package com.eunilsung.talk.data.remote.linkpreview

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.decode
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import com.eunilsung.talk.util.Log
import com.eunilsung.talk.domain.model.LinkPreview
import com.eunilsung.talk.domain.model.LinkPreviewState
import com.eunilsung.talk.domain.repository.LinkPreviewRepository
import com.eunilsung.talk.domain.util.isPreviewableUrl

/**
 * 채팅 본문의 URL → Open Graph 메타데이터 fetch + 세션 내 메모리 캐시.
 *
 * 받아도 되는 주소([isPreviewableUrl])만 받는다. 리다이렉트는 엔진에 맡기지 않고 직접 따라가며 주소마다 다시 확인한다 —
 * 공개 주소가 사설 주소로 넘기는 것을 막기 위해서다. 본문은 [MAX_HTML_BYTES] 까지만 읽고 나머지는 받지 않는다.
 * og:image 도 받아도 되는 주소일 때만 카드에 싣는다.
 */
class LinkPreviewRepositoryImpl(
    httpClient: HttpClient,
) : LinkPreviewRepository {
    private val httpClient = httpClient.config { followRedirects = false }
    private val cache = mutableMapOf<String, MutableStateFlow<LinkPreviewState>>()
    private val cacheMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val fetchSemaphore = Semaphore(permits = 5)

    /** [url] 의 미리보기 상태 Flow. 캐시 hit 면 그 값, miss 면 백그라운드 fetch. */
    override suspend fun getState(url: String): StateFlow<LinkPreviewState> {
        return cacheMutex.withLock {
            cache.getOrPut(url) {
                val flow = MutableStateFlow<LinkPreviewState>(LinkPreviewState.Loading)
                scope.launch {
                    fetchSemaphore.withPermit {
                        flow.value = runCatching { fetchInternal(url) }
                            .getOrElse {
                                Log.message("[LinkPreview] fetch exception url=$url err=${it.message}")
                                LinkPreviewState.Failure
                            }
                    }
                }
                flow
            }
        }.asStateFlow()
    }

    private suspend fun fetchInternal(url: String): LinkPreviewState {
        if (!isPreviewableUrl(url)) {
            Log.message("[LinkPreview] blocked url=$url")
            return LinkPreviewState.Failure
        }
        val page = withTimeoutOrNull(FETCH_TIMEOUT_MS) { fetchHtml(url) }
        if (page == null) {
            Log.message("[LinkPreview] timeout or fail url=$url")
            return LinkPreviewState.Failure
        }
        val parsed = parseOgMeta(url = page.url, html = page.html)
        val preview = parsed.copy(url = url, imageUrl = parsed.imageUrl.takeIf(::isPreviewableUrl).orEmpty())
        Log.message(
            "[LinkPreview] parsed url=$url htmlLen=${page.html.length} " +
                "title='${preview.title.take(40)}' " +
                "image='${preview.imageUrl.take(60)}'"
        )
        return if (preview.title.isBlank() && preview.description.isBlank() && preview.imageUrl.isBlank()) {
            LinkPreviewState.Failure
        } else {
            LinkPreviewState.Success(preview)
        }
    }

    /** 리다이렉트를 [MAX_REDIRECTS] 번까지 따라가 HTML 을 읽는다. 막힌 주소로 넘어가거나 HTML 이 아니면 null. */
    private suspend fun fetchHtml(url: String): FetchedPage? {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val outcome = runCatching {
                httpClient.prepareGet(current) { browserHeaders() }.execute { response -> readPage(current, response) }
            }.getOrElse {
                Log.message("[LinkPreview] request exception url=$current err=${it.message}")
                return null
            }
            when (outcome) {
                is FetchOutcome.Page -> return outcome.page
                is FetchOutcome.Redirect -> {
                    if (!isPreviewableUrl(outcome.location)) {
                        Log.message("[LinkPreview] blocked redirect url=$current → ${outcome.location}")
                        return null
                    }
                    current = outcome.location
                }
                FetchOutcome.Unusable -> return null
            }
        }
        Log.message("[LinkPreview] too many redirects url=$url")
        return null
    }

    private suspend fun readPage(url: String, response: HttpResponse): FetchOutcome {
        Log.message("[LinkPreview] response url=$url status=${response.status.value} ctype=${response.contentType()}")
        if (response.status.value in REDIRECT_STATUSES) {
            val location = response.headers[HttpHeaders.Location] ?: return FetchOutcome.Unusable
            val resolved = runCatching { URLBuilder(url).takeFrom(location).buildString() }.getOrNull()
                ?: return FetchOutcome.Unusable
            return FetchOutcome.Redirect(resolved)
        }
        val ctype = response.contentType()?.toString()?.lowercase().orEmpty()
        if (!ctype.contains("text/html") && !ctype.contains("application/xhtml")) {
            Log.message("[LinkPreview] non-html url=$url ctype=$ctype")
            return FetchOutcome.Unusable
        }
        val html = runCatching {
            val head = response.bodyAsChannel().readRemaining(MAX_HTML_BYTES)
            (response.charset() ?: Charsets.UTF_8).newDecoder().decode(head)
        }.getOrNull() ?: return FetchOutcome.Unusable
        return FetchOutcome.Page(FetchedPage(url = url, html = html))
    }

    private fun HttpRequestBuilder.browserHeaders() {
        headers {
            append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36"
            )
            append(
                HttpHeaders.Accept,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            append(HttpHeaders.AcceptLanguage, "ko-KR,ko;q=0.9,en;q=0.8")
            append(HttpHeaders.AcceptEncoding, "identity")
        }
    }

    /** 마지막으로 읽은 주소와 그 HTML 앞부분. 상대 경로 og:image 는 이 주소를 기준으로 푼다. */
    private class FetchedPage(val url: String, val html: String)

    private sealed interface FetchOutcome {
        class Page(val page: FetchedPage) : FetchOutcome
        class Redirect(val location: String) : FetchOutcome
        data object Unusable : FetchOutcome
    }

    companion object {
        /** 본문에서 읽는 최대 바이트. OG 메타는 head 에 있어 앞부분이면 충분하다. */
        internal const val MAX_HTML_BYTES = 1024L * 1024L
        internal const val MAX_REDIRECTS = 3
        private const val FETCH_TIMEOUT_MS = 5_000L
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}

/** HTML head 의 OG / twitter / 기본 메타 태그를 정규식으로 추출. */
internal fun parseOgMeta(url: String, html: String): LinkPreview {
    fun find(vararg keys: String): String {
        for (key in keys) {
            val regex = Regex(
                """<meta\s+[^>]*?(?:property|name)\s*=\s*["']${Regex.escape(key)}["'][^>]*?content\s*=\s*["']([^"']+)["']""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val m1 = regex.find(html)?.groupValues?.getOrNull(1)
            if (!m1.isNullOrBlank()) return decodeHtmlEntities(m1.trim())
            val regexRev = Regex(
                """<meta\s+[^>]*?content\s*=\s*["']([^"']+)["'][^>]*?(?:property|name)\s*=\s*["']${Regex.escape(key)}["']""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val m2 = regexRev.find(html)?.groupValues?.getOrNull(1)
            if (!m2.isNullOrBlank()) return decodeHtmlEntities(m2.trim())
        }
        return ""
    }

    val title = find("og:title", "twitter:title").ifBlank {
        Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.trim()
            ?.let { decodeHtmlEntities(it) }
            .orEmpty()
    }
    val description = find("og:description", "twitter:description", "description")
    val rawImage = find("og:image", "twitter:image")
    val siteName = find("og:site_name")

    val imageUrl = resolveAbsoluteUrl(base = url, ref = rawImage)

    return LinkPreview(
        url = url,
        title = title,
        description = description,
        imageUrl = imageUrl,
        siteName = siteName,
    )
}

/** 기본 HTML 엔티티 디코드. */
private fun decodeHtmlEntities(s: String): String {
    return s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
}

/** 상대 URL → 절대 URL. 이미 절대면 그대로. */
private fun resolveAbsoluteUrl(base: String, ref: String): String {
    if (ref.isBlank()) return ""
    if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
    if (ref.startsWith("//")) {
        val scheme = base.substringBefore("://", "https")
        return "$scheme:$ref"
    }
    val origin = run {
        val schemeEnd = base.indexOf("://").takeIf { it >= 0 } ?: return ref
        val afterScheme = base.substring(schemeEnd + 3)
        val pathStart = afterScheme.indexOf('/')
        if (pathStart < 0) base else base.substring(0, schemeEnd + 3 + pathStart)
    }
    return if (ref.startsWith("/")) "$origin$ref" else "$origin/$ref"
}

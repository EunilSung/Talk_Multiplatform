package com.eunilsung.talk.data.remote.linkpreview

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
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

/** 채팅 본문의 URL → Open Graph 메타데이터 fetch + 세션 내 메모리 캐시. */
class LinkPreviewRepository(
    private val httpClient: HttpClient,
) {
    private val cache = mutableMapOf<String, MutableStateFlow<LinkPreviewState>>()
    private val cacheMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val fetchSemaphore = Semaphore(permits = 5)

    /** [url] 의 미리보기 상태 Flow. 캐시 hit 면 그 값, miss 면 백그라운드 fetch. */
    suspend fun getState(url: String): StateFlow<LinkPreviewState> {
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
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            Log.message("[LinkPreview] unsupported scheme url=$url")
            return LinkPreviewState.Failure
        }
        val response: HttpResponse? = withTimeoutOrNull(5_000) {
            runCatching {
                httpClient.get(url) {
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
            }.getOrElse {
                Log.message("[LinkPreview] request exception url=$url err=${it.message}")
                null
            }
        }
        if (response == null) {
            Log.message("[LinkPreview] timeout or fail url=$url")
            return LinkPreviewState.Failure
        }
        Log.message("[LinkPreview] response url=$url status=${response.status.value} ctype=${response.contentType()}")
        val ctype = response.contentType()?.toString()?.lowercase().orEmpty()
        if (!ctype.contains("text/html") && !ctype.contains("application/xhtml")) {
            Log.message("[LinkPreview] non-html url=$url ctype=$ctype")
            return LinkPreviewState.Failure
        }
        val html = runCatching { response.bodyAsText() }.getOrNull()
            ?: return LinkPreviewState.Failure
        val capped = if (html.length > MAX_HTML_BYTES) html.substring(0, MAX_HTML_BYTES) else html
        val preview = parseOgMeta(url = url, html = capped)
        Log.message(
            "[LinkPreview] parsed url=$url htmlLen=${html.length} " +
                "title='${preview.title.take(40)}' " +
                "image='${preview.imageUrl.take(60)}'"
        )
        return if (preview.title.isBlank() && preview.description.isBlank() && preview.imageUrl.isBlank()) {
            LinkPreviewState.Failure
        } else {
            LinkPreviewState.Success(preview)
        }
    }

    companion object {
        private const val MAX_HTML_BYTES = 2 * 1024 * 1024
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

package com.eunilsung.talk.data.remote.linkpreview

/** Open Graph / meta 태그로부터 파싱한 링크 미리보기 정보. */
data class LinkPreview(
    val url: String,
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val siteName: String = "",
) {
    /** URL 라인에 표시할 도메인(host). */
    val displayDomain: String
        get() {
            val noScheme = url.substringAfter("://", url)
            return noScheme.substringBefore("/").lowercase()
        }
}

sealed interface LinkPreviewState {
    /** fetch 진행 중 — 스켈레톤 표시. */
    data object Loading : LinkPreviewState

    /** OG 파싱 성공 — title/description/image 중 하나라도 있으면 카드 표시. */
    data class Success(val data: LinkPreview) : LinkPreviewState

    /** 시도했으나 미리보기 표시 불가 (4xx/5xx/timeout/HTML 아님 등) — 카드 숨김. */
    data object Failure : LinkPreviewState
}

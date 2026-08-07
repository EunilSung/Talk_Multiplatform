package com.eunilsung.talk.data.remote.share

/** 외부 앱에서 공유 시트로 전달된 데이터 (텍스트/파일). ShareScreen 의 입력값. */
data class SharedContent(
    val text: String = "",
    val filePaths: List<String> = emptyList(),
) {
    val hasText: Boolean get() = text.isNotBlank()
    val hasFiles: Boolean get() = filePaths.isNotEmpty()
    val isEmpty: Boolean get() = !hasText && !hasFiles
}

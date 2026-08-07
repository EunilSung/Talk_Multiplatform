package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.eunilsung.talk.ui.theme.AppColors

/**
 * [source] 안의 [searchWord] 를 찾아 강조 스타일을 입힌다.
 *
 * 주의: 빌더에 이미 쌓인 내용이 [source] 와 같은 문자열이어야 한다. URL 트리밍처럼
 * 빌더 쪽이 더 짧아질 수 있는 경로가 있어, 인덱스를 빌더 길이로 잘라 범위를 벗어나지 않게 한다.
 */
internal fun AnnotatedString.Builder.addSearchHighlight(source: String, searchWord: String) {
    if (searchWord.isBlank()) return
    val builderLen = this.length
    var start = 0
    while (start < source.length) {
        val index = source.indexOf(searchWord, start, ignoreCase = true)
        if (index == -1 || index >= builderLen) break
        val end = (index + searchWord.length).coerceAtMost(builderLen)
        if (end <= index) break
        addStyle(
            style = SpanStyle(
                background = AppColors.Black,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            ),
            start = index,
            end = end,
        )
        start = end
    }
}

/** 검색어만 강조한 단순 텍스트 — 멘션·링크 처리가 필요 없는 곳에서 쓴다. */
fun buildSearchHighlightedText(
    text: String,
    searchWord: String,
): AnnotatedString {
    if (searchWord.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addSearchHighlight(text, searchWord)
    }
}

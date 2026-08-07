package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.eunilsung.talk.util.MENTION_TAG_REGEX
import com.eunilsung.talk.util.hasMentionTags
import com.eunilsung.talk.ui.theme.AppColors

/** 본문에 부착하는 URL 어노테이션 태그 — clickableLinks=false 모드에서 탭 위치 → URL 매핑에 사용. */
internal const val URL_ANNOTATION_TAG = "URL"

internal val URL_REGEX = Regex(
    """(https?://[A-Za-z0-9./?=&%#_\-~+,;:!@()\[\]]+|www\.[A-Za-z0-9./?=&%#_\-~+,;:!@()\[\]]+)""",
    RegexOption.IGNORE_CASE
)

fun extractFirstUrl(text: String): String? {
    val raw = URL_REGEX.find(text)?.value ?: return null
    val trimmed = raw.dropLastWhile { it in TRAILING_PUNCTUATION }
    return if (trimmed.startsWith("http", ignoreCase = true)) trimmed
    else "https://$trimmed"
}

private val TRAILING_PUNCTUATION = setOf('.', ',', ')', ';', '!', '?', ']')

private data class CleanedBody(val text: String, val mentionRanges: List<IntRange>)

private fun stripMentions(raw: String): CleanedBody {
    if (!raw.hasMentionTags()) return CleanedBody(raw, emptyList())
    val sb = StringBuilder()
    val ranges = mutableListOf<IntRange>()
    var last = 0
    MENTION_TAG_REGEX.findAll(raw).forEach { m ->
        sb.append(raw, last, m.range.first)
        val start = sb.length
        sb.append(m.groupValues[1])
        if (sb.length > start) ranges += start until sb.length
        last = m.range.last + 1
    }
    sb.append(raw, last, raw.length)
    return CleanedBody(sb.toString(), ranges)
}

fun buildChatHighlightedText(
    text: String,
    searchWord: String,
    linkColor: Color,
    clickableLinks: Boolean = true,
): AnnotatedString {
    val (body, mentionRanges) = stripMentions(text)

    return buildAnnotatedString {
        var lastIndex = 0
        URL_REGEX.findAll(body).forEach { match ->

            if (match.range.first > lastIndex) {
                append(body.substring(lastIndex, match.range.first))
            }

            val raw = match.value
            val trimmedRight = raw.dropLastWhile { it in TRAILING_PUNCTUATION }

            val url = if (trimmedRight.startsWith("http", ignoreCase = true)) {
                trimmedRight
            } else {
                "https://$trimmedRight"
            }
            if (clickableLinks) {
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            )
                        )
                    )
                ) {
                    append(trimmedRight)
                }
            } else {
                val linkStart = length
                append(trimmedRight)
                addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    linkStart,
                    length,
                )
                addStringAnnotation(tag = URL_ANNOTATION_TAG, annotation = url, start = linkStart, end = length)
            }

            if (raw.length > trimmedRight.length) {
                append(raw.substring(trimmedRight.length))
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < body.length) {
            append(body.substring(lastIndex))
        }

        mentionRanges.forEach { r ->
            val end = (r.last + 1).coerceAtMost(this.length)
            if (r.first < end) {
                addStyle(SpanStyle(color = AppColors.Secondary), start = r.first, end = end)
            }
        }

        addSearchHighlight(body, searchWord)
    }
}

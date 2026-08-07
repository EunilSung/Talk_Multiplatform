package com.eunilsung.talk.util

/** 멘션 태그(`<mention>@이름</mention>`) 상수. */
const val MENTION_OPEN_TAG = "<mention>"
const val MENTION_CLOSE_TAG = "</mention>"

/** 캡처 그룹 1 = 태그 안쪽 텍스트. */
val MENTION_TAG_REGEX =
    Regex("""$MENTION_OPEN_TAG([\s\S]*?)$MENTION_CLOSE_TAG""", RegexOption.IGNORE_CASE)

/** 태그를 벗기고 안쪽 텍스트만 남긴다. */
fun String.stripMentionTags(): String = replace(MENTION_TAG_REGEX, "$1")

/** 태그가 하나라도 있는지 — 없으면 파싱 자체를 건너뛴다. */
fun String.hasMentionTags(): Boolean = contains(MENTION_OPEN_TAG, ignoreCase = true)

/** [text] 의 [start] until [end] 구간을 멘션 태그로 감싼 문자열. */
fun wrapMention(text: CharSequence, start: Int, end: Int): String =
    "$MENTION_OPEN_TAG${text.subSequence(start, end)}$MENTION_CLOSE_TAG"

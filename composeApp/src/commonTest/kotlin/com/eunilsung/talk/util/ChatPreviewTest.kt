package com.eunilsung.talk.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 미리보기 문자열의 이모티콘 토큰 조합/분리 규칙 검증. */
class ChatPreviewTest {

    @Test
    fun 이모티콘만_있으면_id만_남는다() {
        assertEquals("(사회생활_감사해요)", buildEmoticonPreview("(사회생활_감사해요)", ""))
    }

    @Test
    fun 이모티콘과_본문은_공백으로_이어붙인다() {
        assertEquals(
            "(사회생활_감사해요) 오늘 고마웠어요",
            buildEmoticonPreview("(사회생활_감사해요)", "오늘 고마웠어요"),
        )
    }

    @Test
    fun 앞쪽_토큰을_본문과_분리한다() {
        val (token, rest) = splitLeadingToken("(사회생활_감사해요) 오늘 고마웠어요")
        assertEquals("(사회생활_감사해요)", token)
        assertEquals("오늘 고마웠어요", rest)
    }

    @Test
    fun 토큰만_있으면_본문은_빈값이다() {
        val (token, rest) = splitLeadingToken("(사회생활_감사해요)")
        assertEquals("(사회생활_감사해요)", token)
        assertEquals("", rest)
    }

    @Test
    fun 토큰이_없으면_원본을_그대로_돌려준다() {
        val (token, rest) = splitLeadingToken("괄호 없는 일반 대화")
        assertNull(token)
        assertEquals("괄호 없는 일반 대화", rest)
    }

    @Test
    fun 본문_중간의_괄호는_토큰이_아니다() {
        val (token, rest) = splitLeadingToken("오늘 (중요) 회의")
        assertNull(token)
        assertEquals("오늘 (중요) 회의", rest)
    }

    @Test
    fun 조합과_분리는_서로_역연산이다() {
        val (token, rest) = splitLeadingToken(buildEmoticonPreview("(하늘A2_ㅋㅋㅋ)", "ㅋㅋㅋㅋ"))
        assertEquals("(하늘A2_ㅋㅋㅋ)", token)
        assertEquals("ㅋㅋㅋㅋ", rest)
    }
}

package com.eunilsung.talk.util

import kotlin.test.Test
import kotlin.test.assertEquals

/** 멘션 태그 제거(stripMentionTags) 검증. */
class MentionTagsTest {

    @Test
    fun 태그를_벗기고_안쪽_텍스트만_남긴다() {
        assertEquals(
            "@김민준 확인 부탁드립니다",
            "<mention>@김민준</mention> 확인 부탁드립니다".stripMentionTags(),
        )
    }

    @Test
    fun 멘션이_여러_개여도_모두_벗긴다() {
        assertEquals(
            "@김민준 @이서연 회의합시다",
            "<mention>@김민준</mention> <mention>@이서연</mention> 회의합시다"
                .stripMentionTags(),
        )
    }

    @Test
    fun 대소문자가_달라도_벗긴다() {
        assertEquals("@김민준", "<MENTION>@김민준</MENTION>".stripMentionTags())
    }

    @Test
    fun 멘션이_없으면_원본_그대로다() {
        assertEquals("그냥 대화입니다", "그냥 대화입니다".stripMentionTags())
    }

    @Test
    fun 빈_문자열도_안전하다() {
        assertEquals("", "".stripMentionTags())
    }
}

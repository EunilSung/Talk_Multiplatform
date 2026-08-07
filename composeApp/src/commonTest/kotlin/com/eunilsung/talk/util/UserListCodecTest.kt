package com.eunilsung.talk.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 대화방 참여자 목록 문자열(`"id|name;id|name"`) 인코딩/디코딩 검증. */
class UserListCodecTest {

    @Test
    fun 인코딩과_디코딩은_서로_역연산이다() {
        val users = listOf("test1" to "김민준", "test2" to "이서연")
        assertEquals(users, UserListCodec.decode(UserListCodec.encode(users)))
    }

    @Test
    fun 이름이_비어도_id는_살아남는다() {
        assertEquals(listOf("test1" to ""), UserListCodec.decode("test1|"))
    }

    @Test
    fun id가_빈_항목은_버린다() {
        assertEquals(listOf("test2" to "이서연"), UserListCodec.decode("|이름없음;test2|이서연"))
    }

    @Test
    fun 빈_문자열은_빈_목록이다() {
        assertEquals(emptyList(), UserListCodec.decode(""))
        assertEquals(emptyList(), UserListCodec.decodeIds(";;"))
    }

    @Test
    fun contains는_id로만_판단한다() {
        val raw = UserListCodec.encode(listOf("test1" to "김민준", "test2" to "이서연"))
        assertTrue(UserListCodec.contains(raw, "test2"))
        assertFalse(UserListCodec.contains(raw, "김민준"))
        assertFalse(UserListCodec.contains(raw, ""))
    }
}

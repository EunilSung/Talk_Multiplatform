package com.eunilsung.talk.data.sample

import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.testsupport.TestMyInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 대화방별 전송 주체 전환 규칙 검증. */
class ChatSenderOverrideTest {

    private val override = ChatSenderOverride()
    private val other = User(id = "test2", name = "이서연")

    @BeforeTest
    fun setUp() = TestMyInfo.loginAs()

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    @Test
    fun 선택_전에는_전송_주체가_없다() {
        assertNull(override.senderFor("room_direct_1"))
    }

    @Test
    fun 선택한_사용자가_전송_주체가_된다() {
        override.select("room_direct_1", other)
        assertEquals(other, override.senderFor("room_direct_1"))
    }

    @Test
    fun 대화방마다_따로_기억한다() {
        override.select("room_direct_1", other)
        assertNull(override.senderFor("room_group_dev"))
    }

    @Test
    fun null_이면_해제된다() {
        override.select("room_direct_1", other)
        override.select("room_direct_1", null)
        assertNull(override.senderFor("room_direct_1"))
    }

    @Test
    fun 나_자신을_고르면_해제된다() {
        override.select("room_direct_1", other)
        override.select("room_direct_1", User(id = TestMyInfo.ME, name = "김민준"))
        assertNull(override.senderFor("room_direct_1"))
    }

    @Test
    fun 대화방_id가_비면_무시한다() {
        override.select("", other)
        assertEquals(emptyMap(), override.senders.value)
    }
}

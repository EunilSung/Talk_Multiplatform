package com.eunilsung.talk.data.testdata

import com.eunilsung.talk.testsupport.TestMyInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 스크롤·페이징 시연용 긴 방이 의도한 규모를 유지하는지. */
class LongRoomSeedTest {

    @BeforeTest fun setUp() = TestMyInfo.loginAs()
    @AfterTest fun tearDown() = TestMyInfo.clear()

    private fun longRoom() =
        TestChatRooms.seed(TestMyInfo.ME).first { it.id == "room_group_release" }

    @Test
    fun 긴_방은_대화가_30개_이상이다() {
        val chats = TestChats.seed(longRoom(), TestMyInfo.ME)
        assertTrue(chats.size >= 30, "대화 ${chats.size}개 — 30개 이상이어야 스크롤 시연이 된다")
    }

    @Test
    fun 긴_방의_안읽음은_10이다() {
        assertEquals("10", longRoom().unReadCount)
    }
}

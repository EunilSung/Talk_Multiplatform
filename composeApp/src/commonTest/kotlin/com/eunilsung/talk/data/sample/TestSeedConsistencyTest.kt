package com.eunilsung.talk.data.sample

import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.testsupport.TestMyInfo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 시드 데이터 정합성 — 대화방 목록에 보이는 수치가 방 안의 실제 대화와 맞는지.
 *
 * 예전에 안읽음이 12 로 하드코딩됐는데 방에는 대화가 5개뿐이라 시연에서 바로 드러났다.
 */
class TestSeedConsistencyTest {

    @BeforeTest fun setUp() = TestMyInfo.loginAs()
    @AfterTest fun tearDown() = TestMyInfo.clear()

    private fun rooms() = TestChatRooms.seed(TestMyInfo.ME)

    @Test
    fun 안읽음은_실제_대화_수를_넘지_않는다() {
        rooms().forEach { room ->
            val unread = room.unReadCount.toInt()
            val total = TestChats.seed(room, TestMyInfo.ME).size
            assertTrue(
                unread <= total,
                "${room.id}: 안읽음 $unread > 대화 $total",
            )
        }
    }

    @Test
    fun 안읽음은_내가_보낸_대화를_세지_않는다() {
        rooms().forEach { room ->
            val chats = TestChats.seed(room, TestMyInfo.ME)
            val fromOthers = chats.count { !it.isMe }
            assertTrue(
                room.unReadCount.toInt() <= fromOthers,
                "${room.id}: 안읽음 ${room.unReadCount} > 상대 대화 $fromOthers",
            )
        }
    }

    @Test
    fun 안읽음은_마지막_내_대화_이후의_상대_대화_수와_같다() {
        rooms().forEach { room ->
            val trailing = TestChats.seed(room, TestMyInfo.ME).takeLastWhile { !it.isMe }.size
            assertEquals(trailing, room.unReadCount.toInt(), "${room.id} 안읽음")
        }
    }

    @Test
    fun 멘션_수만큼_실제로_나를_멘션한_대화가_있다() {
        val myName = TestAccounts.find(TestMyInfo.ME)?.userName.orEmpty()
        rooms().forEach { room ->
            val mentions = TestChats.seed(room, TestMyInfo.ME)
                .takeLastWhile { !it.isMe }
                .count { it.chatContent.contains("@$myName") }
            assertEquals(mentions, room.mentionCount.toInt(), "${room.id} 멘션")
        }
    }

    @Test
    fun 목록의_마지막_대화_내용은_방의_마지막_대화와_같다() {
        rooms().forEach { room ->
            val last = TestChats.seed(room, TestMyInfo.ME).lastOrNull() ?: return@forEach
            if (last.chatType != Chat.Type.TEXT) return@forEach
            assertEquals(last.chatContent, room.lastChatContent, "${room.id} 마지막 대화")
        }
    }
}

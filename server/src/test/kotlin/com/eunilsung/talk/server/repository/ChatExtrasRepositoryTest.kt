package com.eunilsung.talk.server.repository

import com.eunilsung.talk.server.seed.SeedAccounts
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.NoticeAction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 공감·회수·공지·책갈피 — 누가 무엇을 할 수 있고, 한 일이 다른 사람에게 어떻게 보이는지 본다. */
class ChatExtrasRepositoryTest {

    private lateinit var chats: ChatRepository
    private lateinit var room: String

    @BeforeTest
    fun setUp() {
        TestDatabase.clean()
        SeedAccounts.ensure(UserRepository(TestDatabase.dataSource))
        chats = ChatRepository(TestDatabase.dataSource)
        room = assertNotNull(chats.createRoom("test1", listOf("test2", "test3"))).roomId
    }

    private fun say(sender: String, text: String, id: String = "$sender-$text"): MessageDto =
        assertNotNull(chats.send(room, sender, id, MessageKind.TEXT, text, null))

    private fun stored(id: String, viewer: String = "test1"): MessageDto =
        chats.messages(room, viewer, null, null, 50)!!.first { it.id == id }

    @Test
    fun `공감을 누르면 모두에게 보이고 같은 것을 다시 누르면 꺼진다`() {
        val message = say("test1", "좋은 소식")

        val on = assertNotNull(chats.toggleReaction(room, "test2", message.id, "1"))
        assertEquals(listOf(Triple("test2", "이서연", "1")), on.reactions.map { Triple(it.userId, it.userName, it.kind) })
        assertEquals(1, stored(message.id, viewer = "test3").reactions.size)

        val off = assertNotNull(chats.toggleReaction(room, "test2", message.id, "1"))
        assertTrue(off.reactions.isEmpty())
    }

    @Test
    fun `다른 공감을 누르면 갈아타고 한 사람은 하나만 남는다`() {
        val message = say("test1", "어떻게 생각해?")
        chats.toggleReaction(room, "test2", message.id, "1")
        chats.toggleReaction(room, "test3", message.id, "1")

        val changed = assertNotNull(chats.toggleReaction(room, "test2", message.id, "4"))

        assertEquals(setOf("test2" to "4", "test3" to "1"), changed.reactions.map { it.userId to it.kind }.toSet())
    }

    @Test
    fun `모르는 종류와 방 밖의 사람과 들어오기 전 대화에는 공감할 수 없다`() {
        val message = say("test1", "예전 말")
        chats.invite(room, "test1", listOf("test4"))

        assertNull(chats.toggleReaction(room, "test2", message.id, "9"))
        assertNull(chats.toggleReaction(room, "test5", message.id, "1"))
        assertNull(chats.toggleReaction(room, "test4", message.id, "1"))
        assertNull(chats.toggleReaction(room, "test2", "no-such-message", "1"))
        assertTrue(stored(message.id).reactions.isEmpty())
    }

    @Test
    fun `회수하면 본문과 부가 정보와 공감이 누구에게도 나가지 않는다`() {
        val payload = MessagePayloadDto(emoticonId = "emo_1")
        val message = assertNotNull(chats.send(room, "test1", "secret", MessageKind.EMOTICON, "잘못 보낸 말", payload))
        chats.toggleReaction(room, "test2", message.id, "1")

        val recalled = assertNotNull(chats.recall(room, "test1", message.id))

        assertTrue(recalled.isRecalled)
        listOf(recalled, stored(message.id, "test2"), assertNotNull(chats.room(room, "test3")?.lastMessage)).forEach {
            assertTrue(it.isRecalled)
            assertEquals("", it.content)
            assertNull(it.payload)
            assertTrue(it.reactions.isEmpty())
        }
        assertEquals(message.seq, recalled.seq)
    }

    @Test
    fun `남의 대화와 알림 대화는 회수할 수 없다`() {
        val message = say("test1", "내 말")
        val invite = chats.messages(room, "test1", null, null, 50)!!.first { it.kind == MessageKind.INVITE }

        assertNull(chats.recall(room, "test2", message.id))
        assertNull(chats.recall(room, "test1", invite.id))
        assertNull(chats.recall(room, "test5", message.id))

        assertFalse(stored(message.id).isRecalled)
        assertEquals("내 말", stored(message.id).content)
    }

    @Test
    fun `두 번 회수해도 같은 결과이고 회수된 대화에는 공감할 수 없다`() {
        val message = say("test1", "거둘 말")
        chats.recall(room, "test1", message.id)

        assertTrue(assertNotNull(chats.recall(room, "test1", message.id)).isRecalled)
        assertNull(chats.toggleReaction(room, "test2", message.id, "1"))
    }

    @Test
    fun `공지를 걸면 방에 하나만 남고 등록 알림이 생긴다`() {
        assertEquals("", chats.notice(room, "test1")?.content)

        val first = assertNotNull(chats.setNotice(room, "test1", "내일 10시 회의"))
        val second = assertNotNull(chats.setNotice(room, "test2", "회의는 11시로 변경"))

        assertEquals(MessageKind.NOTICE, first.message.kind)
        assertEquals("내일 10시 회의", first.message.content)
        assertEquals(NoticeAction.ADD, first.message.payload?.noticeAction)
        val current = assertNotNull(chats.notice(room, "test3"))
        assertEquals("회의는 11시로 변경", current.content)
        assertEquals("test2", current.ownerId)
        assertEquals("이서연", current.ownerName)
        assertEquals("대리", current.ownerPositionName)
        assertEquals(second.notice.id, current.id)
    }

    @Test
    fun `공지를 내리면 사라지고 삭제 알림에 내렸던 내용이 남는다`() {
        chats.setNotice(room, "test1", "곧 내릴 공지")

        val change = assertNotNull(chats.deleteNotice(room, "test2"))

        assertEquals("", change.notice.content)
        assertEquals(NoticeAction.DELETE, change.message.payload?.noticeAction)
        assertEquals("곧 내릴 공지", change.message.content)
        assertEquals("", chats.notice(room, "test1")?.content)
        assertNull(chats.deleteNotice(room, "test1"))
    }

    @Test
    fun `방 밖의 사람은 공지를 보거나 바꿀 수 없다`() {
        chats.setNotice(room, "test1", "우리끼리")

        assertNull(chats.notice(room, "test5"))
        assertNull(chats.setNotice(room, "test5", "끼어들기"))
        assertNull(chats.deleteNotice(room, "test5"))
        assertEquals("우리끼리", chats.notice(room, "test1")?.content)
    }

    @Test
    fun `책갈피는 꽂은 사람에게만 보이고 최근 대화부터 나온다`() {
        val first = say("test2", "첫 번째")
        val second = say("test3", "두 번째")

        assertTrue(chats.addBookmark(room, "test1", first.id))
        assertTrue(chats.addBookmark(room, "test1", second.id))
        assertTrue(chats.addBookmark(room, "test1", second.id))

        val mine = assertNotNull(chats.bookmarks(room, "test1"))
        assertEquals(listOf(second.id, first.id), mine.map { it.messageId })
        assertEquals("박도윤", mine.first().senderName)
        assertEquals("두 번째", mine.first().content)
        assertTrue(assertNotNull(chats.bookmarks(room, "test2")).isEmpty())
    }

    @Test
    fun `책갈피를 빼면 사라지고 회수된 대화의 책갈피는 내용이 빈다`() {
        val kept = say("test2", "남길 말")
        val gone = say("test2", "거둘 말")
        chats.addBookmark(room, "test1", kept.id)
        chats.addBookmark(room, "test1", gone.id)

        chats.recall(room, "test2", gone.id)
        assertTrue(chats.removeBookmark(room, "test1", kept.id))

        val mine = assertNotNull(chats.bookmarks(room, "test1"))
        assertEquals(listOf(gone.id to ""), mine.map { it.messageId to it.content })
    }

    @Test
    fun `볼 수 없는 대화에는 책갈피를 꽂을 수 없다`() {
        val message = say("test1", "방 안의 말")

        assertFalse(chats.addBookmark(room, "test5", message.id))
        assertFalse(chats.addBookmark(room, "test1", "no-such-message"))
        assertNull(chats.bookmarks(room, "test5"))
    }
}

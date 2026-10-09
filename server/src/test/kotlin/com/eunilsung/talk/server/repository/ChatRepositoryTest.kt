package com.eunilsung.talk.server.repository

import com.eunilsung.talk.server.seed.SeedAccounts
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 대화 저장소 — 실제 PostgreSQL 에 쿼리를 돌려 방·대화·읽음 규칙을 확인한다. */
class ChatRepositoryTest {

    private lateinit var chats: ChatRepository

    @BeforeTest
    fun setUp() {
        TestDatabase.clean()
        SeedAccounts.ensure(UserRepository(TestDatabase.dataSource))
        chats = ChatRepository(TestDatabase.dataSource)
    }

    private fun directRoom(a: String = "test1", b: String = "test2"): String =
        assertNotNull(chats.createRoom(a, listOf(b))).roomId

    private fun groupRoom(creator: String = "test1", vararg others: String = arrayOf("test2", "test3")): String =
        assertNotNull(chats.createRoom(creator, others.toList())).roomId

    private fun say(roomId: String, sender: String, text: String, id: String = "$sender-$text"): MessageDto =
        assertNotNull(chats.send(roomId, sender, id, MessageKind.TEXT, text, null))

    @Test
    fun `1대1 방은 누가 먼저 열어도 같은 방이다`() {
        val first = directRoom("test1", "test2")
        val second = directRoom("test2", "test1")

        assertEquals(first, second)
        assertEquals(1, chats.rooms("test1").size)
        assertEquals(listOf("test1", "test2"), chats.rooms("test2").single().members.map { it.id }.sorted())
    }

    @Test
    fun `나만 넣으면 나와의 대화방이 하나만 생긴다`() {
        val first = assertNotNull(chats.createRoom("test1", listOf("test1"))).roomId
        val second = assertNotNull(chats.createRoom("test1", listOf("test1"))).roomId

        assertEquals(first, second)
        assertEquals(listOf("test1"), chats.rooms("test1").single().members.map { it.id })
    }

    @Test
    fun `단체방은 만들 때마다 새 방이고 초대 알림이 남는다`() {
        val change = assertNotNull(chats.createRoom("test1", listOf("test2", "test3")))
        val another = groupRoom()

        assertNotEquals(change.roomId, another)
        val invite = assertNotNull(change.message)
        assertEquals(MessageKind.INVITE, invite.kind)
        assertEquals("test1", invite.senderId)
        assertEquals(listOf("이서연", "박도윤"), invite.payload?.targetNames)
    }

    @Test
    fun `없는 사용자가 섞이면 방을 만들지 않는다`() {
        assertNull(chats.createRoom("test1", listOf("test2", "nobody")))
        assertTrue(chats.rooms("test1").isEmpty())
    }

    @Test
    fun `대화는 방 안에서 1번부터 차례로 번호가 붙는다`() {
        val room = directRoom()

        val seqs = listOf(say(room, "test1", "a"), say(room, "test2", "b"), say(room, "test1", "c")).map { it.seq }

        assertEquals(listOf(1L, 2L, 3L), seqs)
    }

    @Test
    fun `같은 id 로 다시 보내면 대화가 늘지 않고 같은 것을 돌려준다`() {
        val room = directRoom()

        val first = say(room, "test1", "hello", id = "same-id")
        val retry = say(room, "test1", "hello", id = "same-id")

        assertEquals(first.seq, retry.seq)
        assertEquals(1, chats.messages(room, "test1", null, null, 50)?.size)
        assertEquals(2L, say(room, "test1", "next").seq)
    }

    @Test
    fun `참여자가 아니면 보낼 수도 읽을 수도 없다`() {
        val room = directRoom("test1", "test2")
        say(room, "test1", "secret")

        assertNull(chats.send(room, "test3", "x", MessageKind.TEXT, "hi", null))
        assertNull(chats.messages(room, "test3", null, null, 50))
        assertNull(chats.room(room, "test3"))
        assertNull(chats.unreadCounts(room, "test3", "test1-secret"))
        assertFalse(chats.markRead(room, "test3", "test1-secret"))
        assertFalse(chats.rename(room, "test3", "hacked"))
        assertNull(chats.invite(room, "test3", listOf("test4")))
    }

    @Test
    fun `받은 대화는 안읽음으로 잡히고 보낸 사람은 읽은 것으로 친다`() {
        val room = directRoom()
        say(room, "test1", "a")
        say(room, "test1", "b")

        assertEquals(0, chats.room(room, "test1")?.unreadCount)
        assertEquals(2, chats.room(room, "test2")?.unreadCount)
        assertEquals(listOf(1, 1), chats.messages(room, "test1", null, null, 50)?.map { it.unreadCount })
    }

    @Test
    fun `읽으면 내 안읽음과 말풍선의 안읽음 수가 함께 내려간다`() {
        val room = groupRoom()
        val first = say(room, "test1", "a")
        val second = say(room, "test1", "b")
        assertEquals(2, second.unreadCount)

        assertTrue(chats.markRead(room, "test2", first.id))

        assertEquals(1, chats.room(room, "test2")?.unreadCount)
        val counts = chats.messages(room, "test1", null, null, 50)!!.filter { it.kind == MessageKind.TEXT }
        assertEquals(listOf(1, 2), counts.map { it.unreadCount })
    }

    @Test
    fun `읽은 자리는 뒤로 가지 않는다`() {
        val room = directRoom()
        val first = say(room, "test1", "a")
        val second = say(room, "test1", "b")

        assertTrue(chats.markRead(room, "test2", second.id))
        assertFalse(chats.markRead(room, "test2", first.id))
        assertFalse(chats.markRead(room, "test2", second.id))

        assertEquals(0, chats.room(room, "test2")?.unreadCount)
    }

    @Test
    fun `안읽음 수만 따로 물으면 지정한 대화부터 돌려준다`() {
        val room = directRoom()
        say(room, "test1", "a")
        val second = say(room, "test1", "b")
        val third = say(room, "test1", "c")
        chats.markRead(room, "test2", second.id)

        val counts = assertNotNull(chats.unreadCounts(room, "test1", second.id))

        assertEquals(listOf(second.id to 0, third.id to 1), counts.map { it.messageId to it.unreadCount })
    }

    @Test
    fun `뒤쪽과 앞쪽으로 나눠 받을 수 있다`() {
        val room = directRoom()
        val all = (1..5).map { say(room, "test1", "m$it") }

        val latest = chats.messages(room, "test2", null, null, 2)
        val newer = chats.messages(room, "test2", all[1].id, null, 2)
        val older = chats.messages(room, "test2", null, all[3].id, 2)

        assertEquals(listOf("m4", "m5"), latest?.map { it.content })
        assertEquals(listOf("m3", "m4"), newer?.map { it.content })
        assertEquals(listOf("m2", "m3"), older?.map { it.content })
    }

    @Test
    fun `나중에 초대받은 사람은 그 전 대화를 보지 못하고 초대 알림부터 본다`() {
        val room = groupRoom("test1", "test2", "test3")
        say(room, "test1", "before")

        val change = assertNotNull(chats.invite(room, "test2", listOf("test4")))
        say(room, "test1", "after")

        assertEquals(listOf("최지우"), change.message?.payload?.targetNames)
        val seen = assertNotNull(chats.messages(room, "test4", null, null, 50))
        assertEquals(listOf(MessageKind.INVITE, MessageKind.TEXT), seen.map { it.kind })
        assertEquals("after", seen.last().content)
        assertEquals(2, chats.room(room, "test4")?.unreadCount)
    }

    @Test
    fun `이미 있는 사람만 초대하면 아무 일도 일어나지 않는다`() {
        val room = groupRoom()
        val before = chats.messages(room, "test1", null, null, 50)?.size

        val change = assertNotNull(chats.invite(room, "test1", listOf("test2")))

        assertNull(change.message)
        assertEquals(before, chats.messages(room, "test1", null, null, 50)?.size)
    }

    @Test
    fun `1대1 방에 사람을 부르면 단체방이 되고 둘은 새 1대1 방을 열 수 있다`() {
        val room = directRoom("test1", "test2")
        chats.invite(room, "test1", listOf("test3"))

        val newDirect = directRoom("test1", "test2")

        assertNotEquals(room, newDirect)
        assertEquals(3, chats.room(room, "test1")?.members?.size)
    }

    @Test
    fun `나가면 내 목록에서 사라지고 남은 사람에게 퇴장 알림이 남는다`() {
        val room = groupRoom()

        val change = assertNotNull(chats.leave(room, "test2"))

        assertEquals(MessageKind.EXIT, change.message?.kind)
        assertEquals("test2", change.message?.senderId)
        assertTrue(chats.rooms("test2").isEmpty())
        assertNull(chats.send(room, "test2", "x", MessageKind.TEXT, "hi", null))
        val remaining = assertNotNull(chats.room(room, "test1"))
        assertTrue(remaining.members.single { it.id == "test2" }.hasLeft)
        assertEquals(listOf("test1", "test3"), chats.activeMemberIds(room).sorted())
    }

    @Test
    fun `나간 사람은 말풍선의 안읽음 수에서 빠진다`() {
        val room = groupRoom()
        val message = say(room, "test1", "a")
        assertEquals(2, message.unreadCount)

        chats.leave(room, "test2")

        val counts = chats.messages(room, "test1", null, null, 50)!!.first { it.id == message.id }
        assertEquals(1, counts.unreadCount)
    }

    @Test
    fun `나갔던 1대1 방을 다시 열면 같은 방으로 돌아오되 예전 대화는 보이지 않는다`() {
        val room = directRoom("test1", "test2")
        say(room, "test1", "old")
        chats.leave(room, "test2")

        val reopened = directRoom("test2", "test1")
        say(room, "test1", "new")

        assertEquals(room, reopened)
        assertEquals(listOf("new"), chats.messages(room, "test2", null, null, 50)?.map { it.content })
        assertEquals(1, chats.room(room, "test2")?.unreadCount)
    }

    @Test
    fun `방 이름은 모두에게 바뀌고 알림 끄기는 나에게만 적용된다`() {
        val room = groupRoom()

        assertTrue(chats.rename(room, "test2", "점심 모임"))
        assertTrue(chats.setMuted(room, "test2", true))

        assertEquals("점심 모임", chats.room(room, "test1")?.title)
        assertEquals(true, chats.room(room, "test2")?.isMuted)
        assertEquals(false, chats.room(room, "test1")?.isMuted)
    }

    @Test
    fun `답장과 이모티콘의 부가 정보가 그대로 돌아온다`() {
        val room = directRoom()
        val payload = MessagePayloadDto(emoticonId = "emo_1", replyToId = "orig", replyAuthorName = "이서연", replyText = "원문")

        chats.send(room, "test1", "r1", MessageKind.REPLY, "답장", payload)

        val stored = chats.messages(room, "test2", null, null, 50)!!.single()
        assertEquals(MessageKind.REPLY, stored.kind)
        assertEquals(payload, stored.payload)
        assertEquals("김민준", stored.senderName)
    }
}

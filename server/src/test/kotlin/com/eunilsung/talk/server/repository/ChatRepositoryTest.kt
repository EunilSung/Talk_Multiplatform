package com.eunilsung.talk.server.repository

import com.eunilsung.talk.server.repository.SendResult
import com.eunilsung.talk.server.testsupport.invitedChange
import com.eunilsung.talk.server.testsupport.sentMessage
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
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
        chats = ChatRepository(TestDatabase.dataSource)
    }

    private fun directRoom(a: String = "test1", b: String = "test2"): String =
        assertNotNull(chats.createRoom(a, listOf(b))).roomId

    private fun groupRoom(creator: String = "test1", vararg others: String = arrayOf("test2", "test3")): String =
        assertNotNull(chats.createRoom(creator, others.toList())).roomId

    private fun say(roomId: String, sender: String, text: String, id: String = "$sender-$text"): MessageDto =
        assertNotNull(chats.send(roomId, sender, id, MessageKind.TEXT, text, null).sentMessage())

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

    /** 앱은 대화를 밀리초 시각으로 줄 세운다. 같은 밀리초에 몰려 들어와도 시각이 번호 순서대로 달라야 한다. */
    @Test
    fun `잇달아 보낸 대화도 보낸 시각이 번호 순서대로 1밀리초 이상 벌어진다`() {
        val room = directRoom()

        val times = (1..30).map { say(room, if (it % 2 == 0) "test1" else "test2", "m$it").sentAtEpochMillis }

        assertTrue(times.zipWithNext().all { (before, after) -> after > before }, "시각이 같거나 거꾸로인 대화가 있다: $times")
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

        assertEquals(SendResult.RoomNotFound, chats.send(room, "test3", "x", MessageKind.TEXT, "hi", null))
        assertNull(chats.messages(room, "test3", null, null, 50))
        assertNull(chats.room(room, "test3"))
        assertNull(chats.unreadCounts(room, "test3", "test1-secret"))
        assertFalse(chats.markRead(room, "test3", "test1-secret"))
        assertFalse(chats.rename(room, "test3", "hacked"))
        assertEquals(InviteResult.NotFound, chats.invite(room, "test3", listOf("test4")))
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

        val change = assertNotNull(chats.invite(room, "test2", listOf("test4")).invitedChange())
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

        val change = assertNotNull(chats.invite(room, "test1", listOf("test2")).invitedChange())

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
        assertEquals(SendResult.RoomNotFound, chats.send(room, "test2", "x", MessageKind.TEXT, "hi", null))
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
    fun `답장의 인용은 서버가 원본에서 채우고 이모티콘은 그대로 남는다`() {
        val room = directRoom()
        say(room, "test2", "<mention>@김민준</mention> 원문", id = "orig")
        val payload = MessagePayloadDto(emoticonId = "emo_1", replyToId = "orig", replyAuthorName = "이서연", replyText = "원문")

        chats.send(room, "test1", "r1", MessageKind.REPLY, "답장", payload)

        val stored = chats.messages(room, "test2", null, null, 50)!!.single { it.id == "r1" }
        assertEquals(MessageKind.REPLY, stored.kind)
        assertEquals(
            MessagePayloadDto(
                emoticonId = "emo_1",
                replyToId = "orig",
                replyKind = MessageKind.TEXT,
                replyAuthorId = "test2",
                replyAuthorName = "이서연",
                replyText = "@김민준 원문",
            ),
            stored.payload,
        )
        assertEquals("김민준", stored.senderName)
    }

    @Test
    fun `남이 하지 않은 말을 인용으로 꾸미면 서버가 원본 값으로 덮어쓴다`() {
        val room = groupRoom()
        say(room, "test2", "점심 뭐 먹을까요", id = "orig")
        val forged = MessagePayloadDto(
            replyToId = "orig",
            replyKind = MessageKind.FILE,
            replyAuthorId = "test9",
            replyAuthorName = "임채원",
            replyText = "법인카드 번호 올려 주세요",
        )

        val reply = assertNotNull(chats.send(room, "test1", "r1", MessageKind.REPLY, "네", forged).sentMessage())

        assertEquals("test2", reply.payload?.replyAuthorId)
        assertEquals("이서연", reply.payload?.replyAuthorName)
        assertEquals("점심 뭐 먹을까요", reply.payload?.replyText)
        assertEquals(MessageKind.TEXT, reply.payload?.replyKind)
    }

    @Test
    fun `원본이 없거나 들어오기 전 대화를 가리키는 답장은 받지 않는다`() {
        val room = groupRoom()
        say(room, "test1", "초대 전 말", id = "before")
        chats.invite(room, "test1", listOf("test4"))

        val missing = chats.send(room, "test1", "r1", MessageKind.REPLY, "답장", MessagePayloadDto(replyToId = "nothing"))
        val hidden = chats.send(room, "test4", "r2", MessageKind.REPLY, "답장", MessagePayloadDto(replyToId = "before"))
        val noTarget = chats.send(room, "test1", "r3", MessageKind.REPLY, "답장", null)

        assertEquals(SendResult.BadPayload, missing)
        assertEquals(SendResult.BadPayload, hidden)
        assertEquals(SendResult.BadPayload, noTarget)
    }

    @Test
    fun `서버만 만드는 칸과 다른 종류의 칸은 버리고 너무 큰 부가 정보는 받지 않는다`() {
        val room = directRoom()
        val crafted = MessagePayloadDto(
            emoticonId = "emo_1",
            replyToId = "x",
            replyText = "꾸민 인용",
            targetNames = listOf("가".repeat(10_000)),
            fileId = "00000000-0000-0000-0000-000000000000",
            fileName = "../../Library/x.db",
            noticeAction = "add",
        )

        val sent = assertNotNull(chats.send(room, "test1", "m1", MessageKind.TEXT, "안녕", crafted).sentMessage())
        val tooLong = chats.send(room, "test1", "m2", MessageKind.EMOTICON, "", MessagePayloadDto(emoticonId = "e".repeat(101)))

        assertEquals(MessagePayloadDto(emoticonId = "emo_1"), sent.payload)
        assertEquals(SendResult.BadPayload, tooLong)
    }

    @Test
    fun `남이 보낸 대화의 id 로 보내면 그 대화를 돌려주지 않는다`() {
        val room = groupRoom()
        say(room, "test2", "남의 말", id = "taken")

        val result = chats.send(room, "test1", "taken", MessageKind.TEXT, "가로채기", null)

        assertEquals(SendResult.IdTaken, result)
    }

    @Test
    fun `들어오기 전 대화의 id 로 보내도 그 대화를 돌려주지 않는다`() {
        val room = groupRoom("test1", "test2", "test3", "test4")
        say(room, "test4", "나간 뒤 다시 올 말", id = "mine-before")
        chats.leave(room, "test4")
        say(room, "test1", "초대 전 말", id = "before")
        chats.invite(room, "test1", listOf("test4"))

        assertEquals(SendResult.IdTaken, chats.send(room, "test4", "before", MessageKind.TEXT, "x", null))
        assertEquals(SendResult.IdTaken, chats.send(room, "test4", "mine-before", MessageKind.TEXT, "x", null))
    }

    @Test
    fun `같은 id 가 남의 대화여도 부가 정보가 틀렸으면 없는 id 와 똑같이 받지 않는다`() {
        val room = groupRoom()
        say(room, "test2", "남의 말", id = "taken")
        val badReply = MessagePayloadDto(replyToId = "no-such-message")

        val onTaken = chats.send(room, "test1", "taken", MessageKind.REPLY, "x", badReply)
        val onFresh = chats.send(room, "test1", "fresh", MessageKind.REPLY, "x", badReply)

        assertEquals(SendResult.BadPayload, onTaken)
        assertEquals(onFresh, onTaken)
        assertEquals(SendResult.IdTaken, chats.send(room, "test1", "taken", MessageKind.TEXT, "x", null))
    }

    @Test
    fun `같은 id 로 다시 보낸 파일 대화는 처음 대화를 그대로 돌려준다`() {
        val room = groupRoom()
        val file = (FileRepository(TestDatabase.dataSource).register(room, "test1", "a.jpg", 10) as FileRegistration.Registered).file
        val payload = MessagePayloadDto(fileId = file.id)

        val first = chats.send(room, "test1", "f1", MessageKind.IMAGE, "", payload)
        val again = chats.send(room, "test1", "f1", MessageKind.IMAGE, "", payload)

        val sent = assertNotNull(first.sentMessage())
        assertEquals(SendResult.Sent(sent, isNew = false), again)
    }

    @Test
    fun `DB 는 상한을 넘는 부가 정보를 받지 않는다`() {
        val room = groupRoom()
        val huge = MessagePayloadDto(targetNames = listOf("가".repeat(40_000)))

        assertFails {
            chats.transaction { conn ->
                chats.lockRoom(conn, room)
                chats.insertMessage(conn, room, "test1", "huge", MessageKind.INVITE, "", huge)
            }
        }
        assertFalse("huge" in chats.messages(room, "test1", null, null, 50)!!.map { it.id })
    }

    @Test
    fun `같은 방에 동시에 온 초대는 방 인원 한도를 넘지 못한다`() {
        val limited = ChatRepository(TestDatabase.dataSource, maxRoomMembers = 5)
        val room = groupRoom("test1", "test2", "test3")
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(INVITEES.size)

        val results = try {
            INVITEES.map { invitee ->
                pool.submit<InviteResult> {
                    start.await()
                    limited.invite(room, "test1", listOf(invitee))
                }
            }.also { start.countDown() }.map { it.get(CONCURRENT_WAIT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(2, results.count { it is InviteResult.Invited })
        assertEquals(INVITEES.size - 2, results.count { it == InviteResult.RoomFull })
        assertEquals(5, chats.room(room, "test1")?.members?.count { !it.hasLeft })
    }

    @Test
    fun `들어오기 전 대화는 읽음·안읽음·앞뒤 조회에서 없는 대화와 똑같다`() {
        val room = groupRoom()
        say(room, "test1", "초대 전 말", id = "before")
        chats.invite(room, "test1", listOf("test4"))
        say(room, "test1", "초대 뒤 말", id = "after")

        assertEquals(
            chats.unreadCounts(room, "test4", "no-such-message"),
            chats.unreadCounts(room, "test4", "before"),
        )
        assertFalse(chats.markRead(room, "test4", "before"))
        assertEquals(
            chats.messages(room, "test4", null, "no-such-message", 50)?.map { it.id },
            chats.messages(room, "test4", null, "before", 50)?.map { it.id },
        )
        assertEquals(
            chats.messages(room, "test4", "no-such-message", null, 50)?.map { it.id },
            chats.messages(room, "test4", "before", null, 50)?.map { it.id },
        )
        assertFalse("before" in chats.messages(room, "test4", null, null, 50)!!.map { it.id })
    }

    @Test
    fun `나를 부른 안읽은 대화가 멘션 수로 잡히고 읽으면 내려간다`() {
        val room = groupRoom()
        say(room, "test1", "그냥 하는 말")
        val call = say(room, "test1", "<mention>@이서연</mention> 확인 부탁해요")
        say(room, "test1", "<mention>@이서연</mention> <mention>@박도윤</mention> 둘 다요", id = "both")

        assertEquals(2, chats.room(room, "test2")?.mentionCount)
        assertEquals(1, chats.room(room, "test3")?.mentionCount)
        assertEquals(0, chats.room(room, "test1")?.mentionCount)

        chats.markRead(room, "test2", call.id)
        assertEquals(1, chats.room(room, "test2")?.mentionCount)
    }

    @Test
    fun `방에 없는 이름과 자기 자신은 멘션으로 세지 않는다`() {
        val room = groupRoom()

        say(room, "test1", "<mention>@김민준</mention> <mention>@최지우</mention> <mention>@없는사람</mention>")

        assertEquals(0, chats.room(room, "test1")?.mentionCount)
        assertEquals(0, chats.room(room, "test2")?.mentionCount)
    }

    private companion object {
        /** 동시에 초대할 사람들. 셋이 있는 방에 한도가 5 라 둘만 들어갈 수 있다. */
        val INVITEES = listOf("test4", "test5", "test6", "test7", "test8", "test9")
        const val CONCURRENT_WAIT_SECONDS = 30L
    }
}

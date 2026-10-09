package com.eunilsung.talk.server.repository

import com.eunilsung.talk.server.seed.SeedAccounts
import com.eunilsung.talk.server.testsupport.TestDatabase
import com.eunilsung.talk.shared.api.CreateVoteRequest
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.VoteDto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 투표 — 표가 어떻게 세어지고, 누가 무엇을 할 수 있는지 본다. */
class VoteRepositoryTest {

    private lateinit var chats: ChatRepository
    private lateinit var votes: VoteRepository
    private lateinit var room: String

    @BeforeTest
    fun setUp() {
        TestDatabase.clean()
        SeedAccounts.ensure(UserRepository(TestDatabase.dataSource))
        chats = ChatRepository(TestDatabase.dataSource)
        votes = VoteRepository(chats)
        room = assertNotNull(chats.createRoom("test1", listOf("test2", "test3"))).roomId
    }

    private fun lunchVote(multiSelect: Boolean = false): VoteDto =
        assertNotNull(
            votes.create(room, "test1", CreateVoteRequest("점심 메뉴", listOf("한식", "중식", "일식"), multiSelect = multiSelect))
        ).vote

    private fun counts(vote: VoteDto?): List<Int> = assertNotNull(vote).items.map { it.voteCount }

    @Test
    fun `투표를 만들면 항목이 순서대로 생기고 방에 알림이 남는다`() {
        val change = assertNotNull(votes.create(room, "test1", CreateVoteRequest("점심 메뉴", listOf("한식", "중식"))))

        assertEquals(listOf(0 to "한식", 1 to "중식"), change.vote.items.map { it.idx to it.content })
        assertEquals("test1", change.vote.writerId)
        assertFalse(change.vote.isClosed)
        assertEquals(MessageKind.VOTE, change.message.kind)
        assertEquals("점심 메뉴", change.message.content)
        assertEquals(change.vote.id, change.message.payload?.vote?.id)
        assertEquals(1, chats.room(room, "test2")?.unreadCount?.minus(1))
    }

    @Test
    fun `표를 주면 득표수가 오르고 다시 고르면 옮겨 간다`() {
        val vote = lunchVote()

        assertEquals(listOf(1, 0, 0), counts(votes.cast(room, "test2", vote.id, listOf(0))))
        assertEquals(listOf(1, 1, 0), counts(votes.cast(room, "test3", vote.id, listOf(1))))
        val moved = assertNotNull(votes.cast(room, "test2", vote.id, listOf(2)))

        assertEquals(listOf(0, 1, 1), counts(moved))
        assertEquals(setOf("test2" to 2, "test3" to 1), moved.voters.map { it.userId to it.itemIdx }.toSet())
        assertEquals("이서연", moved.voters.first { it.userId == "test2" }.userName)
    }

    @Test
    fun `표를 비워 보내면 거둔다`() {
        val vote = lunchVote()
        votes.cast(room, "test2", vote.id, listOf(0))

        val cleared = assertNotNull(votes.cast(room, "test2", vote.id, emptyList()))

        assertEquals(listOf(0, 0, 0), counts(cleared))
        assertTrue(cleared.voters.isEmpty())
    }

    @Test
    fun `하나만 고르는 투표에는 여럿을 줄 수 없고 여럿 고르는 투표에는 줄 수 있다`() {
        val single = lunchVote()
        val multi = lunchVote(multiSelect = true)

        assertNull(votes.cast(room, "test2", single.id, listOf(0, 1)))
        assertEquals(listOf(1, 1, 0), counts(votes.cast(room, "test2", multi.id, listOf(0, 1))))
        assertEquals(listOf(0, 0, 0), counts(votes.vote(room, "test2", single.id)))
    }

    @Test
    fun `없는 항목과 방 밖의 사람의 표는 받지 않는다`() {
        val vote = lunchVote()

        assertNull(votes.cast(room, "test2", vote.id, listOf(9)))
        assertNull(votes.cast(room, "test5", vote.id, listOf(0)))
        assertNull(votes.vote(room, "test5", vote.id))
        assertNull(votes.votes(room, "test5"))
        assertEquals(listOf(0, 0, 0), counts(votes.vote(room, "test1", vote.id)))
    }

    @Test
    fun `만든 사람만 끝낼 수 있고 끝나면 결과가 알림으로 남는다`() {
        val vote = lunchVote()
        votes.cast(room, "test2", vote.id, listOf(1))
        votes.cast(room, "test3", vote.id, listOf(1))

        assertNull(votes.close(room, "test2", vote.id))
        val change = assertNotNull(votes.close(room, "test1", vote.id))

        assertTrue(change.vote.isClosed)
        assertEquals(MessageKind.VOTE_CLOSED, change.message.kind)
        assertEquals(listOf(0, 2, 0), change.message.payload?.vote?.items?.map { it.voteCount })
        assertTrue(assertNotNull(change.message.payload?.vote).voters.isEmpty())
        assertNull(votes.close(room, "test1", vote.id))
    }

    @Test
    fun `끝난 투표에는 표를 주거나 거둘 수 없다`() {
        val vote = lunchVote()
        votes.cast(room, "test2", vote.id, listOf(0))
        votes.close(room, "test1", vote.id)

        assertNull(votes.cast(room, "test3", vote.id, listOf(1)))
        assertNull(votes.cast(room, "test2", vote.id, emptyList()))
        assertEquals(listOf(1, 0, 0), counts(votes.vote(room, "test1", vote.id)))
    }

    @Test
    fun `방의 투표 목록은 최근 것부터 나오고 다른 방의 투표는 섞이지 않는다`() {
        val first = lunchVote()
        val second = assertNotNull(votes.create(room, "test2", CreateVoteRequest("회식 날짜", listOf("금요일", "토요일")))).vote
        val otherRoom = assertNotNull(chats.createRoom("test1", listOf("test4"))).roomId
        votes.create(otherRoom, "test1", CreateVoteRequest("다른 방 투표", listOf("가", "나")))

        assertEquals(listOf(second.id, first.id), votes.votes(room, "test3")?.map { it.id })
        assertNull(votes.vote(otherRoom, "test1", first.id))
    }
}

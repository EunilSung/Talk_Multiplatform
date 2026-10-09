package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.mapper.ChatMapper
import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.data.mapper.ServerChatMapper
import com.eunilsung.talk.data.remote.server.ServerFileStore
import com.eunilsung.talk.data.sample.ChatSenderOverride
import com.eunilsung.talk.data.sample.LocalChatRoomListRepositoryImpl
import com.eunilsung.talk.data.sample.LocalChatRoomRepositoryImpl
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.VoteForm
import com.eunilsung.talk.domain.model.VoteFormItem
import com.eunilsung.talk.testsupport.FakeFileMetadataResolver
import com.eunilsung.talk.testsupport.FakeLoginRepository
import com.eunilsung.talk.testsupport.FakeTalkServer
import com.eunilsung.talk.testsupport.TestMyInfo
import com.eunilsung.talk.testsupport.createTestDatabase
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 서버 투표 저장소 — 만든 투표가 대화창에 나타나고, 표와 결과가 서버 것 그대로 보이는지 본다. */
class VoteRepositoryImplTest {

    private val roomId = "room-a"

    private lateinit var server: FakeTalkServer
    private lateinit var localChats: LocalChatRoomRepositoryImpl
    private lateinit var repo: VoteRepositoryImpl

    @BeforeTest
    fun setUp() {
        TestMyInfo.loginAs()
        val database = createTestDatabase()
        val deviceFiles = FakeFileMetadataResolver()
        server = FakeTalkServer().apply { addRoom(roomId, "test2" to "이서연", "test3" to "박도윤") }
        val chatRoomMapper = ChatRoomMapper()
        localChats = LocalChatRoomRepositoryImpl(
            chatMapper = ChatMapper(),
            chatRoomMapper = chatRoomMapper,
            chatRoomListRepository = LocalChatRoomListRepositoryImpl(
                FakeLoginRepository(), MapSettings(), chatRoomMapper, database, seedsSampleRooms = false,
            ),
            fileMetadataResolver = deviceFiles,
            senderOverride = ChatSenderOverride(),
            database = database,
            seedsSampleChats = false,
        )
        repo = VoteRepositoryImpl(server, localChats, ServerChatMapper(ServerFileStore(MapSettings(), server, deviceFiles)))
    }

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    private val lunch = VoteForm(
        title = "점심 메뉴",
        items = listOf(VoteFormItem(0, "한식"), VoteFormItem(1, "중식"), VoteFormItem(2, "  ")),
    )

    private suspend fun chats(): List<Chat.Item> = localChats.getChats(roomId).first()

    @Test
    fun 투표를_만들면_빈_항목은_빠지고_대화창에_투표_말풍선이_나타난다() = runTest {
        val voteId = repo.createVote(roomId, lunch).getOrThrow()

        val bubble = chats().single()
        assertEquals(Chat.Type.VOTE, bubble.chatType)
        assertEquals("점심 메뉴", bubble.title)
        assertEquals(voteId, bubble.vote?.id)
        assertEquals(listOf("한식", "중식"), bubble.vote?.items)
    }

    @Test
    fun 항목이_모자라거나_서버에_닿지_못하면_만들어지지_않는다() = runTest {
        val tooFew = repo.createVote(roomId, VoteForm(title = "하나뿐", items = listOf(VoteFormItem(0, "유일"))))
        server.isReachable = false
        val unreachable = repo.createVote(roomId, lunch)

        assertTrue(tooFew.isFailure)
        assertTrue(unreachable.isFailure)
        assertTrue(chats().isEmpty())
        assertTrue(server.calls.none { it.startsWith("createVote:$roomId:하나뿐") })
    }

    @Test
    fun 표를_주면_서버가_센_득표가_돌아오고_다시_투표하면_내_표가_빠진다() = runTest {
        val voteId = repo.createVote(roomId, lunch).getOrThrow()
        server.cast(roomId, voteId, "test2", "이서연", listOf(1))

        val voted = assertNotNull(repo.submitVote(roomId, voteId, setOf(1)))
        assertEquals(listOf(0, 2), voted.items.map { it.nVote })
        assertEquals(2, voted.participantCount)
        assertTrue(voted.hasVoted("test1"))
        assertEquals(1, voted.myItemIdx("test1"))

        val cleared = assertNotNull(repo.reVote(roomId, voteId))
        assertEquals(listOf(0, 1), cleared.items.map { it.nVote })
        assertFalse(cleared.hasVoted("test1"))
    }

    @Test
    fun 목록에는_내가_만들었는지와_투표했는지가_함께_나온다() = runTest {
        val voteId = repo.createVote(roomId, lunch).getOrThrow()
        repo.submitVote(roomId, voteId, setOf(0))

        val summary = repo.fetchVotes(roomId).single()

        assertEquals("점심 메뉴", summary.title)
        assertEquals(2, summary.itemCount)
        assertEquals(1, summary.participantCount)
        assertTrue(summary.isMine)
        assertTrue(summary.hasVoted)
        assertFalse(summary.isClosed)
    }

    @Test
    fun 투표를_끝내면_결과_말풍선이_나타나고_더_투표할_수_없다() = runTest {
        val voteId = repo.createVote(roomId, lunch).getOrThrow()
        server.cast(roomId, voteId, "test2", "이서연", listOf(0))
        server.cast(roomId, voteId, "test3", "박도윤", listOf(0))

        repo.closeVote(roomId, voteId, "점심 메뉴")

        val result = chats().last()
        assertEquals(Chat.Type.VOTE_COMPLETE, result.chatType)
        assertEquals(listOf("한식" to 2, "중식" to 0), result.voteComplete?.items?.map { it.content to it.nVote })
        assertTrue(assertNotNull(repo.fetchVote(roomId, voteId)).isClosed)
        assertNull(repo.submitVote(roomId, voteId, setOf(1)))
    }

    @Test
    fun 서버에_닿지_못하면_투표_목록은_비고_상세는_없다() = runTest {
        val voteId = repo.createVote(roomId, lunch).getOrThrow()
        server.isReachable = false

        assertTrue(repo.fetchVotes(roomId).isEmpty())
        assertNull(repo.fetchVote(roomId, voteId))
        assertNull(repo.submitVote(roomId, voteId, setOf(0)))
    }
}

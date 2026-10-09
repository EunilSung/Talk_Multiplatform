package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.mapper.ChatMapper
import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.data.mapper.ServerChatMapper
import com.eunilsung.talk.data.remote.server.ServerFileStore
import com.eunilsung.talk.data.remote.server.UserDirectory
import com.eunilsung.talk.data.sample.ChatSenderOverride
import com.eunilsung.talk.data.sample.LocalChatRoomListRepositoryImpl
import com.eunilsung.talk.data.sample.LocalChatRoomRepositoryImpl
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.Notice
import com.eunilsung.talk.shared.api.ServerEvent
import com.eunilsung.talk.testsupport.FakeFileMetadataResolver
import com.eunilsung.talk.testsupport.FakeLoginRepository
import com.eunilsung.talk.testsupport.FakeServerEvents
import com.eunilsung.talk.testsupport.FakeTalkServer
import com.eunilsung.talk.testsupport.TestMyInfo
import com.eunilsung.talk.testsupport.awaitUntil
import com.eunilsung.talk.testsupport.createTestDatabase
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 서버 대화 저장소의 공감·회수·공지·책갈피 — 서버가 받아 준 것만 화면에 반영되는지 본다. */
class ChatRoomExtrasRepositoryImplTest {

    private val roomId = "room-a"
    private val otherRoomId = "room-b"

    private lateinit var database: AppDatabase
    private lateinit var server: FakeTalkServer
    private lateinit var events: FakeServerEvents
    private lateinit var repo: ChatRoomRepositoryImpl

    /** 기기의 파일과, 어떤 서버 파일이 기기 어디에 있는지의 기록. 저장소를 새로 만들어도(앱 재시작) 남는다. */
    private val deviceFiles = FakeFileMetadataResolver()
    private val fileSettings = MapSettings()


    @BeforeTest
    fun setUp() {
        TestMyInfo.loginAs()
        database = createTestDatabase()
        server = FakeTalkServer().apply {
            addRoom(roomId, "test2" to "이서연")
            addRoom(otherRoomId, "test3" to "박도윤")
        }
        events = FakeServerEvents()
        repo = newRepository()
    }

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    private fun newRepository(): ChatRoomRepositoryImpl {
        val chatRoomMapper = ChatRoomMapper()
        val localList = LocalChatRoomListRepositoryImpl(
            FakeLoginRepository(), MapSettings(), chatRoomMapper, database, seedsSampleRooms = false,
        )
        val local = LocalChatRoomRepositoryImpl(
            chatMapper = ChatMapper(),
            chatRoomMapper = chatRoomMapper,
            chatRoomListRepository = localList,
            fileMetadataResolver = deviceFiles,
            senderOverride = ChatSenderOverride(),
            database = database,
            seedsSampleChats = false,
        )
        val fileStore = ServerFileStore(fileSettings, server, deviceFiles)
        return ChatRoomRepositoryImpl(local, server, events, ServerChatMapper(fileStore), fileStore, deviceFiles, UserDirectory(), database)
    }

    private suspend fun chats(): List<Chat.Item> = repo.getChats(roomId).first()

    private suspend fun chat(id: String): Chat.Item = chats().first { it.chatID == id }

    private fun peerSays(text: String) = server.receive(roomId, "test2", "이서연", text)

    @Test
    fun 공감을_누르면_서버가_돌려준_상태가_말풍선에_나타나고_다시_누르면_꺼진다() = runTest {
        val message = peerSays("좋은 소식")
        repo.fetchChats(roomId)

        repo.sendEmpathy(roomId, message.id, "2")
        assertEquals(listOf("test1"), chat(message.id).empathy.empathy2.map { it.id })

        repo.sendEmpathy(roomId, message.id, "2")
        assertTrue(chat(message.id).empathy.empathy2.isEmpty())
    }

    @Test
    fun 서버에_닿지_못하면_공감은_화면에_나타나지_않는다() = runTest {
        val message = peerSays("좋은 소식")
        repo.fetchChats(roomId)
        server.isReachable = false

        repo.sendEmpathy(roomId, message.id, "2")

        assertTrue(chat(message.id).empathy.empathy2.isEmpty())
    }

    @Test
    fun 상대가_누른_공감이_알림으로_오면_말풍선에_나타난다() = runTest {
        repo.fetchChats(roomId)
        repo.sendTextChat(roomId, "내 말")
        val mine = chats().single()

        events.push(
            ServerEvent(ServerEvent.TYPE_MESSAGE_UPDATED, roomId, server.react(roomId, mine.chatID, "test2", "이서연", "0"))
        )

        awaitUntil { chat(mine.chatID).empathy.empathy0.map { it.name } == listOf("이서연") }
    }

    @Test
    fun 회수하면_본문이_비고_회수된_대화로_남는다() = runTest {
        repo.fetchChats(roomId)
        repo.sendTextChat(roomId, "잘못 보낸 말")
        val mine = chats().single()

        repo.recallChat(roomId, mine.chatID)

        val recalled = chats().single()
        assertTrue(recalled.isRecalled)
        assertEquals("", recalled.chatContent)
        assertEquals(mine.chatID, recalled.chatID)
    }

    @Test
    fun 서버가_회수를_받아_주지_않으면_대화는_그대로다() = runTest {
        val theirs = peerSays("남의 말")
        repo.fetchChats(roomId)

        repo.recallChat(roomId, theirs.id)
        server.isReachable = false
        repo.recallChat(roomId, theirs.id)

        assertFalse(chat(theirs.id).isRecalled)
        assertEquals("남의 말", chat(theirs.id).chatContent)
    }

    @Test
    fun 상대가_회수했다는_알림이_오면_내_화면에서도_본문이_사라진다() = runTest {
        val theirs = peerSays("곧 거둘 말")
        repo.fetchChats(roomId)

        events.push(ServerEvent(ServerEvent.TYPE_MESSAGE_UPDATED, roomId, server.recall(roomId, theirs.id)))

        awaitUntil { chat(theirs.id).isRecalled }
        assertEquals("", chat(theirs.id).chatContent)
    }

    @Test
    fun 꺼져_있던_동안_생긴_공감과_회수는_다시_들어갈_때_반영된다() = runTest {
        val reacted = peerSays("공감받을 말")
        val recalled = peerSays("회수될 말")
        repo.fetchChats(roomId)

        server.react(roomId, reacted.id, "test2", "이서연", "3")
        server.recall(roomId, recalled.id)
        repo = newRepository()
        repo.fetchChats(roomId)

        assertEquals(listOf("test2"), chat(reacted.id).empathy.empathy3.map { it.id })
        assertTrue(chat(recalled.id).isRecalled)
    }

    @Test
    fun 공지를_걸면_공지_띠와_등록_알림_대화가_나타난다() = runTest {
        repo.fetchChats(roomId)
        assertNull(repo.currentNotice.value)

        repo.addNotice(roomId, "내일 10시 회의")

        val notice = repo.currentNotice.value
        assertEquals("내일 10시 회의", notice?.content)
        assertEquals("김민준", notice?.ownerName)
        val alert = chats().single()
        assertEquals(Chat.Type.NOTICE, alert.chatType)
        assertEquals(Notice.ACTION_ADD, alert.title)
        assertEquals("내일 10시 회의", alert.chatContent)
    }

    @Test
    fun 공지를_내리면_띠가_사라지고_삭제_알림_대화가_남는다() = runTest {
        repo.fetchChats(roomId)
        repo.addNotice(roomId, "곧 내릴 공지")

        repo.deleteNotice(roomId)

        assertNull(repo.currentNotice.value)
        assertEquals(listOf(Notice.ACTION_ADD, Notice.ACTION_DELETE), chats().map { it.title })
    }

    @Test
    fun 서버에_닿지_못하면_공지는_바뀌지_않는다() = runTest {
        repo.fetchChats(roomId)
        repo.addNotice(roomId, "유지될 공지")
        server.isReachable = false

        repo.addNotice(roomId, "반영되면 안 되는 공지")
        repo.deleteNotice(roomId)

        assertEquals("유지될 공지", repo.currentNotice.value?.content)
    }

    @Test
    fun 상대가_공지를_바꿨다는_알림이_오면_띠가_바뀐다() = runTest {
        repo.fetchChats(roomId)

        server.putNotice(roomId, "test2", "이서연", "상대가 건 공지")
        events.push(ServerEvent(ServerEvent.TYPE_NOTICE, roomId))

        awaitUntil { repo.currentNotice.value?.content == "상대가 건 공지" }
        assertEquals("이서연", repo.currentNotice.value?.ownerName)
    }

    @Test
    fun 다른_방으로_옮기면_앞_방의_공지와_책갈피가_따라오지_않는다() = runTest {
        val message = peerSays("책갈피할 말")
        repo.fetchChats(roomId)
        repo.addNotice(roomId, "A 방 공지")
        repo.addBookmark(roomId, chat(message.id))
        assertEquals(1, repo.bookmarks.value.size)

        server.isReachable = false
        repo.fetchChats(otherRoomId)

        assertNull(repo.currentNotice.value)
        assertTrue(repo.bookmarks.value.isEmpty())
    }

    @Test
    fun 책갈피를_꽂고_빼면_서버의_목록대로_보인다() = runTest {
        val first = peerSays("첫 번째")
        val second = peerSays("두 번째")
        repo.fetchChats(roomId)

        repo.addBookmark(roomId, chat(first.id))
        repo.addBookmark(roomId, chat(second.id))
        assertEquals(listOf("두 번째", "첫 번째"), repo.bookmarks.value.map { it.content })
        assertEquals("이서연", repo.bookmarks.value.first().userName)

        repo.deleteBookmark(roomId, second.id)
        assertEquals(listOf(first.id), repo.bookmarks.value.map { it.chatId })
    }

    @Test
    fun 책갈피는_다시_켜도_서버에서_돌아온다() = runTest {
        val message = peerSays("남길 말")
        repo.fetchChats(roomId)
        repo.addBookmark(roomId, chat(message.id))

        repo = newRepository()
        repo.fetchChats(roomId)

        assertEquals(listOf(message.id), repo.bookmarks.value.map { it.chatId })
    }
}

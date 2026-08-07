package com.eunilsung.talk.data.testdata

import com.eunilsung.talk.data.local.FileMetadata
import com.eunilsung.talk.data.local.FileMetadataResolver
import com.eunilsung.talk.data.mapper.ChatMapper
import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.testsupport.FakeLoginRepository
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

/**
 * 대화 저장소 통합 — 공감 토글 / 공지 / 책갈피 / 회수.
 *
 * 상태가 DB 를 왕복하며 문자열로 저장되는 구간(`isRecalled` 는 "1"/"0")이라
 * 매핑이 어긋나면 조용히 표시만 틀어진다. 재조회로 확인한다.
 */
class LocalChatRoomRepositoryTest {

    private val roomId = "room_group_dev"

    private lateinit var database: AppDatabase
    private lateinit var roomList: LocalChatRoomListRepositoryImpl
    private lateinit var repo: LocalChatRoomRepositoryImpl

    /** 파일 전송 경로는 이 테스트 대상이 아니라 최소 동작만 흉내 낸다. */
    private class NoopFileMetadataResolver : FileMetadataResolver {
        override suspend fun resolve(path: String) = FileMetadata("f.txt", ".txt", "", 1)
        override suspend fun readBytes(path: String): ByteArray? = null
        override suspend fun writeCacheFile(filename: String, bytes: ByteArray): String? = null
        override suspend fun saveDownloadedFile(filename: String, bytes: ByteArray): String? = null
        override suspend fun saveToGallery(filename: String, bytes: ByteArray): String? = null
        override suspend fun saveVideoToGallery(filename: String, bytes: ByteArray): String? = null
        override suspend fun findDownloadedFile(filename: String): String? = null
    }

    @BeforeTest
    fun setUp() {
        TestMyInfo.loginAs()
        database = createTestDatabase()
        val settings = MapSettings()
        val chatRoomMapper = ChatRoomMapper()
        roomList = LocalChatRoomListRepositoryImpl(
            FakeLoginRepository(), settings, chatRoomMapper, database,
        )
        repo = LocalChatRoomRepositoryImpl(
            chatMapper = ChatMapper(),
            chatRoomMapper = chatRoomMapper,
            chatRoomListRepository = roomList,
            fileMetadataResolver = NoopFileMetadataResolver(),
            senderOverride = ChatSenderOverride(),
            database = database,
        )
    }

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    private suspend fun chats(): List<Chat.Item> = repo.getChats(roomId).first()

    /** 대화 시드는 대화방 행이 있어야 만들어지므로 방 목록을 먼저 채운다. */
    private suspend fun seedRoom() {
        roomList.fetchChatRooms()
        repo.fetchChats(roomId)
    }

    /** 시드 대화를 채우고 그중 하나를 돌려준다. */
    private suspend fun seedAndFirstChat(): Chat.Item {
        seedRoom()
        return chats().first()
    }

    @Test
    fun 같은_공감을_두_번_누르면_취소된다() = runTest {
        val target = seedAndFirstChat()

        repo.sendEmpathy(roomId, target.chatID, "1")
        val after = chats().first { it.chatID == target.chatID }
        assertTrue(
            after.empathy.empathy1.any { it.id == TestMyInfo.ME },
            "첫 클릭에는 내 반응이 붙어야 한다",
        )

        repo.sendEmpathy(roomId, target.chatID, "1")
        val toggled = chats().first { it.chatID == target.chatID }
        assertFalse(
            toggled.empathy.empathy1.any { it.id == TestMyInfo.ME },
            "같은 반응을 다시 누르면 빠져야 한다",
        )
    }

    @Test
    fun 다른_공감으로_바꾸면_이전_반응은_사라진다() = runTest {
        val target = seedAndFirstChat()

        repo.sendEmpathy(roomId, target.chatID, "1")
        repo.sendEmpathy(roomId, target.chatID, "2")

        val after = chats().first { it.chatID == target.chatID }
        assertFalse(after.empathy.empathy1.any { it.id == TestMyInfo.ME })
        assertTrue(after.empathy.empathy2.any { it.id == TestMyInfo.ME })
    }

    @Test
    fun 공지_등록은_currentNotice_에_반영되고_대화도_남는다() = runTest {
        seedRoom()
        val before = chats().size

        repo.addNotice(roomId, "금요일 3시 전체 회의")

        val notice = repo.currentNotice.value
        assertNotNull(notice)
        assertEquals("금요일 3시 전체 회의", notice.content)

        val added = chats()
        assertEquals(before + 1, added.size)
        val noticeChat = added.last()
        assertEquals(Chat.Type.NOTICE, noticeChat.chatType)
        // 본문은 chatContent, 등록/삭제 구분은 title.
        assertEquals("금요일 3시 전체 회의", noticeChat.chatContent)
        assertEquals(com.eunilsung.talk.domain.model.Notice.ACTION_ADD, noticeChat.title)
    }

    @Test
    fun 공지_삭제는_해제하고_삭제_대화를_남긴다() = runTest {
        seedRoom()
        repo.addNotice(roomId, "임시 공지")

        repo.deleteNotice(roomId)

        assertNull(repo.currentNotice.value)
        val last = chats().last()
        assertEquals(Chat.Type.NOTICE, last.chatType)
        assertEquals(com.eunilsung.talk.domain.model.Notice.ACTION_DELETE, last.title)
        assertEquals("임시 공지", last.chatContent)
    }

    @Test
    fun 공지는_재조회해도_유지된다() = runTest {
        seedRoom()
        repo.addNotice(roomId, "유지되는 공지")

        repo.clearCurrentRoom(roomId)
        assertNull(repo.currentNotice.value)

        repo.fetchChats(roomId)
        assertEquals("유지되는 공지", repo.currentNotice.value?.content)
    }

    @Test
    fun 책갈피는_추가와_해제가_된다() = runTest {
        val target = seedAndFirstChat()

        repo.addBookmark(roomId, target)
        assertEquals(listOf(target.chatID), repo.bookmarks.value.map { it.chatId })

        repo.deleteBookmark(roomId, target.chatID)
        assertTrue(repo.bookmarks.value.isEmpty())
    }

    @Test
    fun 책갈피는_재조회해도_유지된다() = runTest {
        val target = seedAndFirstChat()
        repo.addBookmark(roomId, target)

        repo.clearCurrentRoom(roomId)
        repo.fetchBookmarks(roomId)

        assertEquals(listOf(target.chatID), repo.bookmarks.value.map { it.chatId })
    }

    /** 진입 전 안읽음이 있는 방을 골라 id 와 카운트를 돌려준다. */
    private suspend fun unreadRoom(): Pair<String, String> {
        roomList.fetchChatRooms()
        val room = roomList.getChatRooms().first()
            .first { (it.unReadCount.toIntOrNull() ?: 0) > 0 }
        return room.id to room.unReadCount
    }

    @Test
    fun 읽음처리는_대화방_안읽음을_0으로_내린다() = runTest {
        val (unreadRoomId, before) = unreadRoom()
        assertTrue(before.toInt() > 0, "시드에 안읽음이 있는 방이 있어야 한다")

        repo.markChatAsRead(unreadRoomId, lastChatId = "any")

        val after = roomList.getChatRooms().first().first { it.id == unreadRoomId }
        assertEquals("0", after.unReadCount)
        assertEquals("0", after.mentionCount, "멘션 카운트도 함께 내려가야 한다")
    }

    @Test
    fun 읽음처리는_다른_방의_안읽음을_건드리지_않는다() = runTest {
        roomList.fetchChatRooms()
        val unread = roomList.getChatRooms().first()
            .filter { (it.unReadCount.toIntOrNull() ?: 0) > 0 }
        assertTrue(unread.size >= 2, "이 검증에는 안읽음 방이 둘 이상 필요하다")
        val (target, other) = unread[0] to unread[1]

        repo.markChatAsRead(target.id, lastChatId = "any")

        val otherAfter = roomList.getChatRooms().first().first { it.id == other.id }
        assertEquals(other.unReadCount, otherAfter.unReadCount)
    }

    @Test
    fun 읽음처리는_방_id가_비면_아무것도_하지_않는다() = runTest {
        val (unreadRoomId, before) = unreadRoom()

        assertFalse(repo.markChatAsRead("", lastChatId = "any"))

        val after = roomList.getChatRooms().first().first { it.id == unreadRoomId }
        assertEquals(before, after.unReadCount)
    }

    @Test
    fun 회수_플래그는_DB를_왕복해도_살아남는다() = runTest {
        val target = seedAndFirstChat()
        assertFalse(target.isRecalled)

        repo.recallChat(roomId, target.chatID)

        // "1"/"0" 문자열로 저장되는 구간이라 재조회로 확인한다.
        repo.fetchChats(roomId)
        assertTrue(chats().first { it.chatID == target.chatID }.isRecalled)
    }
}

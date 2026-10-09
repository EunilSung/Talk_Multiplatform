package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.mapper.ChatMapper
import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.data.mapper.ServerChatMapper
import com.eunilsung.talk.data.remote.server.ServerFileStore
import com.eunilsung.talk.data.sample.ChatSenderOverride
import com.eunilsung.talk.data.sample.LocalChatRoomListRepositoryImpl
import com.eunilsung.talk.data.sample.LocalChatRoomRepositoryImpl
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.shared.api.ServerEvent
import com.eunilsung.talk.testsupport.FakeFileMetadataResolver
import com.eunilsung.talk.testsupport.FakeLoginRepository
import com.eunilsung.talk.testsupport.FakeServerEvents
import com.eunilsung.talk.testsupport.FakeTalkServer
import com.eunilsung.talk.testsupport.TestMyInfo
import com.eunilsung.talk.testsupport.awaitUntil
import com.eunilsung.talk.testsupport.createTestDatabase
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 서버 대화 저장소 — 서버 대역과 실제 로컬 DB 를 물려, 서버에서 받은 대화가 로컬에 어떻게 쌓이고
 * 내가 쓴 대화의 상태가 어떻게 바뀌는지 본다.
 */
class ChatRoomRepositoryImplTest {

    private val roomId = "room-a"

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
        server = FakeTalkServer().apply { addRoom(roomId, "test2" to "이서연") }
        events = FakeServerEvents()
        repo = newRepository()
    }

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    /** 같은 DB 를 쓰는 저장소를 새로 만든다 — 앱을 껐다 켠 상황이다. */
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
        return ChatRoomRepositoryImpl(local, server, events, ServerChatMapper(fileStore), fileStore, deviceFiles, database)
    }

    private suspend fun chats(): List<Chat.Item> = repo.getChats(roomId).first()

    private suspend fun contents(): List<String> = chats().map { it.chatContent }

    private fun peerSays(text: String) = server.receive(roomId, "test2", "이서연", text)

    @Test
    fun 방에_들어가면_서버의_대화를_받아_순서대로_보여_준다() = runTest {
        peerSays("안녕")
        peerSays("점심 먹었어?")

        repo.fetchChats(roomId)

        assertEquals(listOf("안녕", "점심 먹었어?"), contents())
        assertTrue(chats().all { it.chatStatue == Chat.Statue.COMPLETE && it.user.name == "이서연" })
    }

    @Test
    fun 다시_들어가면_마지막으로_받은_대화_이후만_요청한다() = runTest {
        peerSays("하나")
        repo.fetchChats(roomId)
        peerSays("둘")

        repo.fetchChats(roomId)

        assertEquals(listOf("하나", "둘"), contents())
        assertTrue("messages:$roomId:after=test2-하나" in server.calls)
    }

    @Test
    fun 서버에_닿지_못하면_가지고_있던_대화를_그대로_보여_준다() = runTest {
        peerSays("하나")
        repo.fetchChats(roomId)
        peerSays("둘")
        server.isReachable = false

        repo.fetchChats(roomId)

        assertEquals(listOf("하나"), contents())
    }

    @Test
    fun 한_쪽에_다_못_받은_대화는_이어서_받는다() = runTest {
        peerSays("처음")
        repo.fetchChats(roomId)
        repeat(150) { peerSays("m$it") }

        repo.fetchChats(roomId)

        assertEquals(151, chats().size)
        assertEquals("m149", contents().last())
    }

    @Test
    fun 보낸_대화는_서버가_받으면_완료가_된다() = runTest {
        repo.fetchChats(roomId)

        repo.sendTextChat(roomId, "보냅니다")

        val sent = chats().single()
        assertEquals(Chat.Statue.COMPLETE, sent.chatStatue)
        assertEquals("보냅니다", server.messagesOf(roomId).single().content)
        assertEquals(sent.chatID, server.messagesOf(roomId).single().id)
        assertEquals("1", sent.unReadCount)
    }

    @Test
    fun 서버에_닿지_못하면_실패로_남고_다시_보내면_같은_id_로_한_번만_들어간다() = runTest {
        repo.fetchChats(roomId)
        server.isReachable = false
        repo.sendTextChat(roomId, "끊긴 사이")
        val failed = chats().single()
        assertEquals(Chat.Statue.FAIL, failed.chatStatue)

        server.isReachable = true
        repo.resendFailedChat(roomId, failed.chatID)

        assertEquals(Chat.Statue.COMPLETE, chats().single().chatStatue)
        assertEquals(listOf(failed.chatID), server.messagesOf(roomId).map { it.id })
    }

    @Test
    fun 답장과_이모티콘은_부가_정보와_함께_서버로_간다() = runTest {
        val original = peerSays("원문입니다")
        repo.fetchChats(roomId)

        repo.sendTextChat(roomId, "답장입니다", replyTarget = chats().single(), emoticonId = "emo_1")

        val stored = server.messagesOf(roomId).last()
        assertEquals("reply", stored.kind)
        assertEquals(original.id, stored.payload?.replyToId)
        assertEquals("원문입니다", stored.payload?.replyText)
        assertEquals("emo_1", stored.payload?.emoticonId)
        val shown = chats().last()
        assertEquals(Chat.Type.REPLY, shown.chatType)
        assertEquals("이서연", shown.replyChat.user.name)
        assertEquals("emo_1", shown.emoticon.id)
    }

    @Test
    fun 열어_본_방에_알림으로_온_대화는_바로_나타나고_새_대화_신호가_난다() = runTest {
        repo.fetchChats(roomId)
        val signals = mutableListOf<String>()
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { repo.newChatPush.collect { signals += it } }

        events.push(ServerEvent(ServerEvent.TYPE_MESSAGE, roomId, peerSays("실시간")))

        awaitUntil { contents() == listOf("실시간") }
        awaitUntil { signals == listOf(roomId) }
        collector.cancel()
    }

    @Test
    fun 열어_보지_않은_방의_알림_대화는_저장하지_않는다() = runTest {
        peerSays("예전 대화")

        events.push(ServerEvent(ServerEvent.TYPE_MESSAGE, roomId, peerSays("알림으로 온 대화")))
        events.push(ServerEvent(ServerEvent.TYPE_READ, roomId))
        events.push(ServerEvent(ServerEvent.TYPE_ROOM, roomId))
        awaitUntil { server.calls == listOf("room:$roomId") }

        repo.fetchChats(roomId)
        assertEquals(listOf("예전 대화", "알림으로 온 대화"), contents())
    }

    @Test
    fun 다시_연결되면_열어_본_방의_놓친_대화를_받아_온다() = runTest {
        repo.fetchChats(roomId)
        peerSays("끊긴 동안 온 대화")

        events.connect()

        awaitUntil { contents() == listOf("끊긴 동안 온 대화") }
    }

    @Test
    fun 누군가_읽었다는_알림이_오면_말풍선의_안읽음_수를_다시_받는다() = runTest {
        repo.fetchChats(roomId)
        repo.sendTextChat(roomId, "읽어 주세요")
        val sentId = chats().single().chatID
        assertEquals("1", chats().single().unReadCount)

        server.unreadByMessage[sentId] = 0
        events.push(ServerEvent(ServerEvent.TYPE_READ, roomId))

        awaitUntil { chats().single().unReadCount == "0" }
    }

    @Test
    fun 읽음을_보내면_서버에_읽은_자리가_전해진다() = runTest {
        val last = peerSays("읽을 대화")
        repo.fetchChats(roomId)

        assertTrue(repo.markChatAsRead(roomId, last.id))

        assertEquals(listOf(roomId to last.id), server.readMarks)
    }

    @Test
    fun 보내다_앱이_꺼져_전송_중으로_남은_대화는_다시_켜면_실패로_바뀐다() = runTest {
        repo.fetchChats(roomId)
        val local = LocalChatRoomRepositoryImpl(
            ChatMapper(), ChatRoomMapper(),
            LocalChatRoomListRepositoryImpl(FakeLoginRepository(), MapSettings(), ChatRoomMapper(), database, seedsSampleRooms = false),
            deviceFiles, ChatSenderOverride(), database, seedsSampleChats = false,
        )
        local.appendMyChat(roomId, local.buildTextChat(roomId, "보내다 꺼짐", null, null, Chat.Statue.SENDING))

        repo = newRepository()
        repo.fetchChats(roomId)

        assertEquals(Chat.Statue.FAIL, chats().single().chatStatue)
        assertTrue(server.messagesOf(roomId).isEmpty())
    }

    @Test
    fun 내가_방에서_빠졌다는_알림이_오면_나간_신호가_난다() = runTest {
        repo.fetchChats(roomId)
        val left = mutableListOf<String>()
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { repo.selfLeftPush.collect { left += it } }
        server.removeRoom(roomId)

        events.push(ServerEvent(ServerEvent.TYPE_ROOM, roomId))

        awaitUntil { left == listOf(roomId) }
        collector.cancel()
    }

    @Test
    fun 사진을_보내면_올린_뒤_대화로_가고_내_말풍선은_원본을_그대로_쓴다() = runTest {
        val photo = ByteArray(300) { it.toByte() }
        deviceFiles.put("/gallery/trip.jpg", photo)
        repo.fetchChats(roomId)

        repo.sendFile(roomId, "/gallery/trip.jpg")

        val stored = server.messagesOf(roomId).single()
        assertEquals("image", stored.kind)
        assertEquals("trip.jpg", stored.payload?.fileName)
        assertEquals(300L, stored.payload?.fileSize)
        assertEquals(FakeFileMetadataResolver.IMAGE_SIZE, stored.payload?.imageSize)
        assertTrue(photo.contentEquals(server.uploads[stored.payload?.fileId]))
        val mine = chats().single()
        assertEquals(Chat.Statue.COMPLETE, mine.chatStatue)
        assertEquals(Chat.Type.IMAGE, mine.chatType)
        assertEquals("/gallery/trip.jpg", mine.imagePath)
        assertTrue(server.calls.none { it.startsWith("download") })
    }

    @Test
    fun 파일을_올리지_못하면_실패로_남고_다시_보내면_올라간다() = runTest {
        deviceFiles.put("/docs/report.pdf", ByteArray(64))
        repo.fetchChats(roomId)
        server.isReachable = false
        repo.sendFile(roomId, "/docs/report.pdf")
        val failed = chats().single()
        assertEquals(Chat.Statue.FAIL, failed.chatStatue)
        assertEquals(Chat.Type.FILE, failed.chatType)

        server.isReachable = true
        repo.resendFailedChat(roomId, failed.chatID)

        assertEquals(Chat.Statue.COMPLETE, chats().single().chatStatue)
        assertEquals(listOf(failed.chatID), server.messagesOf(roomId).map { it.id })
        assertEquals("file", server.messagesOf(roomId).single().kind)
    }

    @Test
    fun 받은_사진은_내려받아_기기_경로로_말풍선에_연결된다() = runTest {
        val photo = ByteArray(128) { 7 }
        val message = server.receiveFile(roomId, "test2", "이서연", "image", "cat.png", photo)

        repo.fetchChats(roomId)

        awaitUntil { chats().single().imagePath.isNotBlank() }
        val shown = chats().single()
        assertEquals(Chat.Type.IMAGE, shown.chatType)
        assertEquals("cat.png", shown.originalFileName)
        assertEquals("30:40", shown.imageSize)
        assertTrue(photo.contentEquals(deviceFiles.files[shown.imagePath]))
        assertEquals(message.id, shown.chatID)
    }

    @Test
    fun 한_번_받은_파일은_다시_켜도_또_받지_않는다() = runTest {
        server.receiveFile(roomId, "test2", "이서연", "file", "spec.docx", ByteArray(32))
        repo.fetchChats(roomId)
        awaitUntil { chats().single().imagePath.isNotBlank() }
        val firstPath = chats().single().imagePath

        repo = newRepository()
        repo.fetchChats(roomId)

        assertEquals(firstPath, chats().single().imagePath)
        assertEquals(1, server.calls.count { it.startsWith("download") })
    }

    @Test
    fun 알림으로_온_파일_대화도_내려받는다() = runTest {
        repo.fetchChats(roomId)

        val message = server.receiveFile(roomId, "test2", "이서연", "video", "clip.mp4", ByteArray(256))
        events.push(ServerEvent(ServerEvent.TYPE_MESSAGE, roomId, message))

        awaitUntil { chats().singleOrNull()?.imagePath?.isNotBlank() == true }
        assertEquals(Chat.Type.VIDEO, chats().single().chatType)
    }
}

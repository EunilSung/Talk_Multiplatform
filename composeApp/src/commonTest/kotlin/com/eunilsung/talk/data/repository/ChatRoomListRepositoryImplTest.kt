package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.data.mapper.ServerChatMapper
import com.eunilsung.talk.data.sample.LocalChatRoomListRepositoryImpl
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.shared.api.ServerEvent
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
import kotlin.test.assertTrue

/** 서버 대화방 목록 저장소 — 서버 목록이 로컬에 어떻게 반영되고, 실패했을 때 무엇이 남는지 본다. */
class ChatRoomListRepositoryImplTest {

    private lateinit var server: FakeTalkServer
    private lateinit var events: FakeServerEvents
    private lateinit var login: FakeLoginRepository
    private lateinit var repo: ChatRoomListRepositoryImpl

    @BeforeTest
    fun setUp() {
        TestMyInfo.loginAs()
        server = FakeTalkServer()
        events = FakeServerEvents()
        login = FakeLoginRepository()
        val local = LocalChatRoomListRepositoryImpl(
            login, MapSettings(), ChatRoomMapper(), createTestDatabase(), seedsSampleRooms = false,
        )
        repo = ChatRoomListRepositoryImpl(local, server, events, login, ServerChatMapper())
    }

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    private suspend fun rooms(): List<ChatRoom.Item> = repo.getChatRooms().first()

    @Test
    fun 서버_모드에서는_시연용_방을_채우지_않는다() = runTest {
        repo.fetchChatRooms()

        assertTrue(rooms().isEmpty())
    }

    @Test
    fun 서버의_방을_참여자와_마지막_대화와_안읽음_수까지_보여_준다() = runTest {
        server.addRoom("direct", "test2" to "이서연", unreadCount = 2)
        server.receive("direct", "test2", "이서연", "마지막 말")

        repo.fetchChatRooms()

        val room = rooms().single()
        assertEquals("direct", room.id)
        assertEquals("이서연", room.displayTitle)
        assertEquals("2", room.unReadCount)
        assertEquals("마지막 말", room.lastChatContent)
        assertTrue(room.isDirectRoom)
        assertEquals(2, repo.unreadTotal.first { it == 2 })
    }

    @Test
    fun 단체방은_이름이_없으면_다른_참여자_이름으로_부른다() = runTest {
        server.addRoom("group", "test2" to "이서연", "test3" to "박도윤")

        repo.fetchChatRooms()

        assertEquals("이서연, 박도윤", rooms().single().displayTitle)
        assertEquals(3, rooms().single().displayUserCount)
    }

    @Test
    fun 서버에_닿지_못하면_마지막으로_받은_목록이_남는다() = runTest {
        server.addRoom("a", "test2" to "이서연")
        repo.fetchChatRooms()
        server.isReachable = false

        repo.fetchChatRooms()

        assertEquals(listOf("a"), rooms().map { it.id })
        assertFalse(repo.isFetching.value)
    }

    @Test
    fun 서버_목록에서_빠진_방은_로컬에서도_사라진다() = runTest {
        server.addRoom("a", "test2" to "이서연")
        server.addRoom("b", "test3" to "박도윤")
        repo.fetchChatRooms()
        server.removeRoom("a")

        repo.fetchChatRooms()

        assertEquals(listOf("b"), rooms().map { it.id })
    }

    @Test
    fun 나가기는_서버가_받아_줬을_때만_로컬에서_지운다() = runTest {
        server.addRoom("a", "test2" to "이서연")
        repo.fetchChatRooms()

        server.isReachable = false
        assertFalse(repo.leaveChatRoom("a"))
        assertEquals(listOf("a"), rooms().map { it.id })

        server.isReachable = true
        assertTrue(repo.leaveChatRoom("a"))
        assertTrue(rooms().isEmpty())
    }

    @Test
    fun 이름_변경과_알림_끄기는_서버에_적용된_뒤_목록에_나타난다() = runTest {
        server.addRoom("group", "test2" to "이서연", "test3" to "박도윤")
        repo.fetchChatRooms()

        assertTrue(repo.renameChatRoom("group", "점심 모임"))
        assertTrue(repo.setChatRoomAlarm("group", "1"))

        assertEquals("점심 모임", rooms().single().title)
        assertEquals("1", rooms().single().isAlarm)
    }

    @Test
    fun 새_대화_알림이_오면_목록을_다시_받는다() = runTest {
        server.addRoom("a", "test2" to "이서연")
        repo.fetchChatRooms()
        val message = server.receive("a", "test2", "이서연", "방금 온 말")

        events.push(ServerEvent(ServerEvent.TYPE_MESSAGE, "a", message))

        awaitUntil { rooms().single().lastChatContent == "방금 온 말" }
    }

    @Test
    fun 로그인하면_알림을_듣기_시작하고_연결될_때마다_목록을_받는다() = runTest {
        server.addRoom("a", "test2" to "이서연")

        login.isLoggedIn.value = true
        awaitUntil { events.listenCount == 1 }
        events.connect()

        awaitUntil { rooms().map { it.id } == listOf("a") }
    }
}

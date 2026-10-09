package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.data.mapper.ServerChatMapper
import com.eunilsung.talk.data.sample.LocalChatRoomListRepositoryImpl
import com.eunilsung.talk.testsupport.FakeLoginRepository
import com.eunilsung.talk.testsupport.FakeServerEvents
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 서버 초대 저장소 — 방 만들기와 초대가 서버를 거쳐 목록에 나타나는지 본다. */
class InviteRepositoryImplTest {

    private lateinit var server: FakeTalkServer
    private lateinit var roomList: ChatRoomListRepositoryImpl
    private lateinit var repo: InviteRepositoryImpl

    @BeforeTest
    fun setUp() {
        TestMyInfo.loginAs()
        server = FakeTalkServer()
        val login = FakeLoginRepository()
        val local = LocalChatRoomListRepositoryImpl(
            login, MapSettings(), ChatRoomMapper(), createTestDatabase(), seedsSampleRooms = false,
        )
        roomList = ChatRoomListRepositoryImpl(local, server, FakeServerEvents(), login, ServerChatMapper())
        repo = InviteRepositoryImpl(server, roomList)
    }

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    @Test
    fun 방_id_가_없으면_새_방을_만들고_목록에_나타난다() = runTest {
        val roomId = repo.inviteUsers("-", listOf("test2" to "이서연"), existingUserCount = 0)

        assertEquals(listOf(roomId), roomList.getChatRooms().first().map { it.id })
        assertTrue("createRoom:[test2]" in server.calls)
    }

    @Test
    fun 방_id_가_있으면_그_방에_초대한다() = runTest {
        server.addRoom("group", "test2" to "이서연", "test3" to "박도윤")

        val roomId = repo.inviteUsers("group", listOf("test4" to "최지우", "test4" to "최지우"), existingUserCount = 3)

        assertEquals("group", roomId)
        assertTrue("invite:group:[test4]" in server.calls)
        assertEquals(4, roomList.getChatRooms().first().single().displayUserCount)
    }

    @Test
    fun 서버가_받아_주지_않으면_null_이고_목록은_그대로다() = runTest {
        server.isReachable = false

        assertNull(repo.inviteUsers("", listOf("test2" to "이서연"), existingUserCount = 0))
        assertNull(repo.inviteUsers("", emptyList(), existingUserCount = 0))

        assertTrue(roomList.getChatRooms().first().isEmpty())
    }
}

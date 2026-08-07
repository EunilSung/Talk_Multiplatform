package com.eunilsung.talk.data.testdata

import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.testsupport.FakeLoginRepository
import com.eunilsung.talk.testsupport.TestMyInfo
import com.eunilsung.talk.testsupport.createTestDatabase
import com.eunilsung.talk.util.UserListCodec
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 초대 저장소 통합 — 방 생성/참여자 추가 규칙 검증.
 * 시드를 부르지 않은 빈 DB 에서 시작해 초대가 만든 방만 남긴다.
 */
class LocalInviteRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var chatRoomList: LocalChatRoomListRepositoryImpl
    private lateinit var invite: LocalInviteRepositoryImpl

    @BeforeTest
    fun setUp() {
        TestMyInfo.loginAs()
        database = createTestDatabase()
        val mapper = ChatRoomMapper()
        chatRoomList = LocalChatRoomListRepositoryImpl(
            FakeLoginRepository(), MapSettings(), mapper, database,
        )
        invite = LocalInviteRepositoryImpl(chatRoomList, mapper, database)
    }

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    private suspend fun roomOf(roomId: String): ChatRoom.Item =
        chatRoomList.getChatRooms().first().first { it.id == roomId }

    /** existingUserCount 는 로컬 구현이 참조하지 않아 0 으로 고정한다. */
    private suspend fun inviteTo(roomId: String, users: List<Pair<String, String>>): String? =
        invite.inviteUsers(roomId, users, existingUserCount = 0)

    /**
     * 방 id 는 밀리초 단위 시각으로 만들어져 연속 호출이면 같은 값이 나올 수 있다.
     * 실제 사용은 사람이 누르는 속도라 문제되지 않아, 테스트에서만 시각을 벌린다.
     */
    private suspend fun tick() = withContext(Dispatchers.Default) { delay(5) }

    @Test
    fun 새_방에는_나와_초대한_사람이_들어간다() = runTest {
        val roomId = inviteTo("", listOf("test2" to "이서연"))
        assertNotNull(roomId)

        val room = roomOf(roomId)
        assertEquals("2", room.totalUserCount)
        assertEquals(
            listOf(TestMyInfo.ME, "test2"),
            UserListCodec.decodeIds(room.totalUserList),
        )
    }

    @Test
    fun 같은_상대를_다시_초대하면_기존_1대1_방을_쓴다() = runTest {
        val first = inviteTo("", listOf("test2" to "이서연"))
        tick()
        val second = inviteTo("", listOf("test2" to "이서연"))

        assertEquals(first, second)
        assertEquals(1, chatRoomList.getChatRooms().first().size)
    }

    @Test
    fun 상대가_다르면_다른_1대1_방이_생긴다() = runTest {
        val toTest2 = inviteTo("", listOf("test2" to "이서연"))
        tick()
        val toTest3 = inviteTo("", listOf("test3" to "박도윤"))

        assertNotEquals(toTest2, toTest3)
        assertEquals(2, chatRoomList.getChatRooms().first().size)
    }

    @Test
    fun 단체방_제목은_초대한_사람_이름을_잇는다() = runTest {
        val roomId = inviteTo("", listOf("test2" to "이서연", "test3" to "박도윤"))
        assertNotNull(roomId)

        assertEquals("3", roomOf(roomId).totalUserCount)
        assertEquals("이서연, 박도윤", roomOf(roomId).title)
    }

    @Test
    fun 기존_방_초대는_중복을_빼고_추가한다() = runTest {
        val roomId = inviteTo("", listOf("test2" to "이서연", "test3" to "박도윤"))!!

        inviteTo(roomId, listOf("test3" to "박도윤", "test4" to "최지우"))

        val room = roomOf(roomId)
        assertEquals("4", room.totalUserCount)
        assertEquals(
            listOf(TestMyInfo.ME, "test2", "test3", "test4"),
            UserListCodec.decodeIds(room.totalUserList),
        )
    }

    @Test
    fun 이미_다_들어있으면_방은_그대로다() = runTest {
        val roomId = inviteTo("", listOf("test2" to "이서연", "test3" to "박도윤"))!!

        assertEquals(roomId, inviteTo(roomId, listOf("test3" to "박도윤")))
        assertEquals("3", roomOf(roomId).totalUserCount)
    }

    @Test
    fun 초대_대상이_없거나_방이_없으면_null() = runTest {
        assertNull(inviteTo("", emptyList()))
        assertNull(inviteTo("", listOf("" to "이름만")))
        assertNull(inviteTo("없는방", listOf("test2" to "이서연")))
    }

    @Test
    fun 중복_초대자는_한_번만_들어간다() = runTest {
        val roomId = inviteTo(
            "", listOf("test2" to "이서연", "test2" to "이서연", "test3" to "박도윤"),
        )!!
        assertEquals("3", roomOf(roomId).totalUserCount)
    }
}

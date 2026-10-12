package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.UserProfile
import com.eunilsung.talk.domain.repository.InviteRepository
import com.eunilsung.talk.domain.repository.UserProfileRepository
import com.eunilsung.talk.domain.util.UserListCodec
import com.eunilsung.talk.testsupport.FakeChatRoomListRepository
import com.eunilsung.talk.testsupport.FakeLoginRepository
import com.eunilsung.talk.testsupport.TestMyInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 1:1 방 열기와 나간 상대 다시 초대 — 대화함 목록을 보고 언제 새로 만들고 언제 초대하는지 본다. */
class InviteUseCaseTest {

    private class RecordingInvite(private val newRoomId: String? = "new-room") : InviteRepository {
        val calls = mutableListOf<Triple<String, List<Pair<String, String>>, Int>>()
        override suspend fun inviteUsers(
            chatRoomId: String,
            invitedUsers: List<Pair<String, String>>,
            existingUserCount: Int,
        ): String? {
            calls += Triple(chatRoomId, invitedUsers, existingUserCount)
            return if (chatRoomId.isBlank()) newRoomId else chatRoomId
        }
    }

    private class Profiles(private val cached: String? = null, private val fetched: String = "") : UserProfileRepository {
        override suspend fun getCachedProfile(userId: String) = cached?.let { UserProfile(userId = userId, name = it) }
        override suspend fun fetchProfile(userId: String) = Result.success(UserProfile(userId = userId, name = fetched))
    }

    private fun room(id: String, members: List<Pair<String, String>>, exited: List<Pair<String, String>> = emptyList()) =
        ChatRoom.Item(
            id = id,
            myId = ME,
            totalUserList = UserListCodec.encode(members),
            totalUserCount = (members.size + exited.size).toString(),
            exitUserList = UserListCodec.encode(exited),
            exitUserCount = exited.size.toString(),
        )

    @BeforeTest
    fun setUp() = TestMyInfo.loginAs(ME)

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    @Test
    fun 둘만의_방이_있으면_새로_만들지_않고_그_방을_연다() = runTest {
        val invite = RecordingInvite()
        val rooms = FakeChatRoomListRepository(listOf(room("r1", listOf(ME to "나", "test2" to "이서연"))))

        val roomId = StartDirectChatUseCase(CreateChatRoomUseCase(invite, rooms, FakeLoginRepository()), Profiles())("test2")

        assertEquals("r1", roomId)
        assertTrue(invite.calls.isEmpty())
    }

    @Test
    fun 알림방과_단체방은_둘만의_방으로_보지_않는다() = runTest {
        val invite = RecordingInvite()
        val rooms = FakeChatRoomListRepository(
            listOf(
                room("noti\$1", listOf(ME to "나", "test2" to "이서연")),
                room("group", listOf(ME to "나", "test2" to "이서연", "test3" to "박도윤")),
            )
        )

        val roomId = StartDirectChatUseCase(CreateChatRoomUseCase(invite, rooms, FakeLoginRepository()), Profiles(cached = "이서연"))("test2")

        assertEquals("new-room", roomId)
        assertEquals(listOf(Triple("", listOf("test2" to "이서연"), 0)), invite.calls)
    }

    @Test
    fun 받아_둔_프로필이_없으면_서버에서_받은_이름으로_방을_만든다() = runTest {
        val invite = RecordingInvite()

        StartDirectChatUseCase(CreateChatRoomUseCase(invite, FakeChatRoomListRepository(), FakeLoginRepository()), Profiles(fetched = "최지우"))("test4")

        assertEquals(listOf("test4" to "최지우"), invite.calls.single().second)
    }

    @Test
    fun 나를_고르면_나와의_대화방을_찾는다() = runTest {
        val rooms = FakeChatRoomListRepository(listOf(room("me", listOf(ME to "나"))))

        val roomId = StartDirectChatUseCase(CreateChatRoomUseCase(RecordingInvite(), rooms, FakeLoginRepository()), Profiles())(ME)

        assertEquals("me", roomId)
    }

    @Test
    fun 한_명과_방을_만들면_있던_둘만의_방을_연다() = runTest {
        val invite = RecordingInvite()
        val rooms = FakeChatRoomListRepository(listOf(room("r1", listOf(ME to "나", "test2" to "이서연"))))

        val roomId = CreateChatRoomUseCase(invite, rooms, FakeLoginRepository())(listOf("test2" to "이서연"))

        assertEquals("r1", roomId)
        assertTrue(invite.calls.isEmpty())
    }

    @Test
    fun 여러_명이면_늘_새_방을_만든다() = runTest {
        val invite = RecordingInvite()
        val rooms = FakeChatRoomListRepository(listOf(room("r1", listOf(ME to "나", "test2" to "이서연"))))

        val roomId = CreateChatRoomUseCase(invite, rooms, FakeLoginRepository())(listOf("test2" to "이서연", "test3" to "박도윤"))

        assertEquals("new-room", roomId)
        assertEquals(1, invite.calls.size)
    }

    @Test
    fun 상대가_나간_둘만의_방이면_보내기_전에_그_상대를_다시_초대한다() = runTest {
        val invite = RecordingInvite()
        val rooms = FakeChatRoomListRepository(
            listOf(room("r1", members = listOf(ME to "나"), exited = listOf("test2" to "이서연")))
        )

        ReinviteDirectPartnerUseCase(rooms, invite)("r1")

        assertEquals(listOf(Triple("r1", listOf("test2" to "이서연"), 1)), invite.calls)
    }

    @Test
    fun 상대가_남아_있으면_다시_초대하지_않는다() = runTest {
        val invite = RecordingInvite()
        val rooms = FakeChatRoomListRepository(listOf(room("r1", listOf(ME to "나", "test2" to "이서연"))))

        ReinviteDirectPartnerUseCase(rooms, invite)("r1")

        assertTrue(invite.calls.isEmpty())
    }

    private companion object {
        const val ME = "test1"
    }
}

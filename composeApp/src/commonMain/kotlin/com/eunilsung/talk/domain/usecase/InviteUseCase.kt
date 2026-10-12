package com.eunilsung.talk.domain.usecase

import kotlinx.coroutines.flow.first
import com.eunilsung.talk.domain.repository.ChatRoomListRepository
import com.eunilsung.talk.domain.repository.InviteRepository
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.domain.repository.UserProfileRepository

/** Invite 도메인 UseCase 번들. */
data class InviteUseCases(
    val inviteUsers: InviteUsersUseCase,
    val createChatRoom: CreateChatRoomUseCase,
    val startDirectChat: StartDirectChatUseCase,
    val getMyUser: GetMyUserUseCase,
)

/**
 * 대화방 초대 / 생성. chatRoomId 가 blank/"-" 이면 새 방 생성, 아니면 기존 방에 추가.
 * 성공 시 chatRoomId, 실패 시 null 반환.
 */
class InviteUsersUseCase(private val repository: InviteRepository) {
    suspend operator fun invoke(
        chatRoomId: String,
        invitedUsers: List<Pair<String, String>>,
        existingUserCount: Int,
    ): String? = repository.inviteUsers(chatRoomId, invitedUsers, existingUserCount)
}

/**
 * 고른 사람들과 대화방을 연다. 한 명이면 받아 둔 방 중 그 사람과 둘이 쓰는 방을 먼저 찾고, 없을 때만 새로 만든다.
 *
 * 빠른 액션·내 그룹·공유·새 대화 어디서 시작해도 같은 상대면 같은 방이 열린다.
 * @return 방 id. 만들지 못하면 null.
 */
class CreateChatRoomUseCase(
    private val inviteRepository: InviteRepository,
    private val chatRoomListRepository: ChatRoomListRepository,
    private val loginRepository: LoginRepository,
) {
    suspend operator fun invoke(users: List<Pair<String, String>>): String? {
        users.singleOrNull()?.let { (userId, _) -> existingDirectRoomId(userId)?.let { return it } }
        return inviteRepository.inviteUsers(chatRoomId = "", invitedUsers = users, existingUserCount = 0)
    }

    /** 받아 둔 방 중 [userId] 와 둘이 쓰는 방 id. [userId] 가 나면 나와의 대화방. 없으면 null. */
    suspend fun existingDirectRoomId(userId: String): String? {
        val myId = loginRepository.getMyUser().id
        return chatRoomListRepository.getChatRooms().first()
            .firstOrNull { it.isDirectRoomWith(userId, myId) }
            ?.id
    }
}

/**
 * [targetUserId] 와의 1:1 방을 연다. 받아 둔 방 중에 있으면 그 방을, 없으면 새로 만든다.
 *
 * 새 방 제목에 쓸 이름은 받아 둔 프로필에서 먼저 찾고, 없으면 서버에서 받아 온다. 있던 방을 열 때는 찾지 않는다.
 * @return 방 id. 만들지 못하면 null.
 */
class StartDirectChatUseCase(
    private val createChatRoom: CreateChatRoomUseCase,
    private val userProfileRepository: UserProfileRepository,
) {
    suspend operator fun invoke(targetUserId: String): String? =
        createChatRoom.existingDirectRoomId(targetUserId)
            ?: createChatRoom(listOf(targetUserId to nameOf(targetUserId)))

    private suspend fun nameOf(userId: String): String {
        userProfileRepository.getCachedProfile(userId)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        return userProfileRepository.fetchProfile(userId).getOrNull()?.name.orEmpty()
    }
}

/**
 * 상대가 나간 1:1 방에 보내기 전에 그 상대를 다시 초대한다. 그런 방이 아니면 아무 일도 하지 않는다.
 *
 * 나 혼자 남은 1:1 방에 글을 쓰면 상대에게 닿지 않기 때문이다.
 */
class ReinviteDirectPartnerUseCase(
    private val chatRoomListRepository: ChatRoomListRepository,
    private val inviteRepository: InviteRepository,
) {
    suspend operator fun invoke(chatRoomId: String) {
        val room = chatRoomListRepository.getChatRooms().first()
            .firstOrNull { it.id == chatRoomId } ?: return
        val partner = room.abandonedDirectPartner ?: return
        inviteRepository.inviteUsers(chatRoomId, listOf(partner), room.displayUserCount)
    }
}

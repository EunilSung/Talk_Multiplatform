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

/** 고른 사람들과 새 대화방을 만든다. 한 명이면 있던 1:1 방을 돌려줄 수 있다. 만든 방 id, 못 만들면 null. */
class CreateChatRoomUseCase(private val repository: InviteRepository) {
    suspend operator fun invoke(users: List<Pair<String, String>>): String? =
        repository.inviteUsers(chatRoomId = "", invitedUsers = users, existingUserCount = 0)
}

/**
 * [targetUserId] 와의 1:1 방을 연다. 받아 둔 방 중에 있으면 그 방을, 없으면 새로 만든다.
 *
 * 새 방 제목에 쓸 이름은 받아 둔 프로필에서 먼저 찾고, 없으면 서버에서 받아 온다.
 * @return 방 id. 만들지 못하면 null.
 */
class StartDirectChatUseCase(
    private val chatRoomListRepository: ChatRoomListRepository,
    private val inviteRepository: InviteRepository,
    private val userProfileRepository: UserProfileRepository,
    private val loginRepository: LoginRepository,
) {
    suspend operator fun invoke(targetUserId: String): String? {
        val myId = loginRepository.getMyUser().id
        chatRoomListRepository.getChatRooms().first()
            .firstOrNull { it.isDirectRoomWith(targetUserId, myId) }
            ?.let { return it.id }
        return inviteRepository.inviteUsers(
            chatRoomId = "",
            invitedUsers = listOf(targetUserId to nameOf(targetUserId)),
            existingUserCount = 0,
        )
    }

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

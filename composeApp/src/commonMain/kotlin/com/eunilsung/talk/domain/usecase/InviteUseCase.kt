package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.repository.InviteRepository

/** Invite 도메인 UseCase 번들. */
data class InviteUseCases(
    val inviteUsers: InviteUsersUseCase,
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

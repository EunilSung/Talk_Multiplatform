package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.data.remote.server.valueOrNull
import com.eunilsung.talk.domain.repository.ChatRoomListRepository
import com.eunilsung.talk.domain.repository.InviteRepository

/**
 * 서버에 대화방을 만들거나 사람을 초대하는 저장소.
 *
 * 1:1 방이 이미 있는지는 서버가 판단한다. 두 사람이 동시에 서로를 눌러도 방이 하나만 생기려면
 * 그 판단이 한곳에서 일어나야 한다.
 */
class InviteRepositoryImpl(
    private val server: TalkServer,
    private val chatRoomListRepository: ChatRoomListRepository,
) : InviteRepository {

    override suspend fun inviteUsers(
        chatRoomId: String,
        invitedUsers: List<Pair<String, String>>,
        existingUserCount: Int,
    ): String? {
        val userIds = invitedUsers.map { it.first }.filter { it.isNotBlank() }.distinct()
        if (userIds.isEmpty()) return null

        val isNewRoom = chatRoomId.isBlank() || chatRoomId == NEW_ROOM_MARK
        val result = if (isNewRoom) server.createRoom(userIds) else server.invite(chatRoomId, userIds)
        val room = result.valueOrNull() ?: return null

        chatRoomListRepository.fetchChatRooms()
        return room.id
    }

    private companion object {
        const val NEW_ROOM_MARK = "-"
    }
}

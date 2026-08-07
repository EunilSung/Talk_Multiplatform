package com.eunilsung.talk.ui.invite

sealed class InviteMode {
    /**
     * 기존 대화방에 초대.
     * @param createNewRoom 1:1 방이면 true — 기존 방 대신 새 방 생성.
     * @param existingUsers 현재 방 참여자 (id, name). 새 방 생성 시 포함.
     */
    data class InviteToChatRoom(
        val chatRoomId: String,
        val existingUserIds: List<String> = emptyList(),
        val createNewRoom: Boolean = false,
        val existingUsers: List<Pair<String, String>> = emptyList(),
    ) : InviteMode()
    data object CreateChatRoom : InviteMode()
}

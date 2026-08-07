package com.eunilsung.talk.domain.repository

/** 초대 / 대화방 생성 도메인 Repository. */
interface InviteRepository {

    /**
     * 대화방 초대 / 생성.
     * @param chatRoomId 비-blank 이면 그 방에 초대, blank("" 또는 "-")이면 새 방 생성.
     * @param invitedUsers 초대할 `(userId, userName)` 쌍 (최소 1명).
     * @param existingUserCount 초대 직전 방의 참여자 수.
     * @return 성공 시 대화방 ID, 실패 시 null.
     */
    suspend fun inviteUsers(
        chatRoomId: String,
        invitedUsers: List<Pair<String, String>>,
        existingUserCount: Int,
    ): String?
}

package com.eunilsung.talk.ui.share

import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.model.SharedContent
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ChatRoom

/**
 * @param forwardChat 대화방에서 "전달"로 열었을 때 보낼 대화. null 이면 다른 앱에서 받은 [sharedContent] 를 보낸다.
 * @param forwardSourceRoomId [forwardChat] 이 있던 방.
 */
data class ShareUiState(
    val sharedContent: SharedContent = SharedContent(),
    val forwardChat: Chat.Item? = null,
    val forwardSourceRoomId: String = "",
    /** 보낼 수 있는 대화방. 나간 방은 뺀다. */
    val rooms: List<ChatRoom.Item> = emptyList(),
    /** 대화방 탭의 방 이름 검색어. */
    val roomQuery: String = "",
    val selectedUsers: List<User> = emptyList(),
    val selectedChatRoom: ChatRoom.Item? = null,
    val isSubmitting: Boolean = false,
) {
    /** 대화방 탭에 보일 방 — 검색어가 있으면 보이는 이름에 그 말이 든 방만. */
    val visibleRooms: List<ChatRoom.Item>
        get() = if (roomQuery.isBlank()) rooms else rooms.filter { it.matchesTitle(roomQuery) }

    val canSubmit: Boolean
        get() = (selectedUsers.isNotEmpty() xor (selectedChatRoom != null)) && !isSubmitting
}

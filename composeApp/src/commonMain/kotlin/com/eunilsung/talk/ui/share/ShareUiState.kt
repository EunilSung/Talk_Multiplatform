package com.eunilsung.talk.ui.share

import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.model.SharedContent
import com.eunilsung.talk.domain.model.ChatRoom

data class ShareUiState(
    val sharedContent: SharedContent = SharedContent(),
    /** 보낼 수 있는 대화방. 나간 방은 뺀다. */
    val rooms: List<ChatRoom.Item> = emptyList(),
    val selectedUsers: List<User> = emptyList(),
    val selectedChatRoom: ChatRoom.Item? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = (selectedUsers.isNotEmpty() xor (selectedChatRoom != null)) && !isSubmitting
}

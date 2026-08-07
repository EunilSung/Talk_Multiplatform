package com.eunilsung.talk.ui.share

import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.data.remote.share.SharedContent
import com.eunilsung.talk.domain.model.ChatRoom

data class ShareUiState(
    val sharedContent: SharedContent = SharedContent(),
    val selectedUsers: List<User> = emptyList(),
    val selectedChatRoom: ChatRoom.Item? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = (selectedUsers.isNotEmpty() xor (selectedChatRoom != null)) && !isSubmitting
}

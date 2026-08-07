package com.eunilsung.talk.ui.invite

import com.eunilsung.talk.domain.model.User


data class InviteUiState(
    val mode: InviteMode = InviteMode.CreateChatRoom,
    val selectedUsers: List<User> = emptyList(),
    val existingUserIds: Set<String> = emptySet(),
    val isSubmitting: Boolean = false,
)

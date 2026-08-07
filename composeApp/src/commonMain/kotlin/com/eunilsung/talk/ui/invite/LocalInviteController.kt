package com.eunilsung.talk.ui.invite

import com.eunilsung.talk.domain.model.User
import androidx.compose.runtime.staticCompositionLocalOf

class InviteController(
    val selectedUsers: List<User>,
    val existingUserIds: Set<String>,
    val onUserToggle: (User) -> Unit,
) {
    fun isSelected(userId: String?): Boolean {
        val id = userId ?: return false
        return selectedUsers.any { it.id == id }
    }
}

val LocalInviteController = staticCompositionLocalOf<InviteController> {
    error("LocalInviteController not provided")
}

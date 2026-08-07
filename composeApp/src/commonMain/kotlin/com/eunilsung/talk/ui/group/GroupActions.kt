package com.eunilsung.talk.ui.group

import com.eunilsung.talk.domain.model.User

sealed interface GroupActions {
    data class ToggleGroup(val groupId: String) : GroupActions
    data object FetchGroups : GroupActions

    data class OnSearchQueryChange(val query: String) : GroupActions
    data class SetMode(val mode: GroupMode) : GroupActions
    data object OnClearSearch : GroupActions

    data class OnSelectUser(val user: User) : GroupActions

    data class RemoveUserFromGroup(val userId: String, val groupId: String) : GroupActions
    data class MoveUserToGroup(
        val userId: String,
        val fromGroupId: String,
        val toGroupId: String
    ) : GroupActions

    data class RenameGroup(val groupId: String, val newName: String) : GroupActions

    data class DeleteGroup(val groupId: String) : GroupActions

    data class CreateGroup(val name: String) : GroupActions
}

sealed interface GroupEvent {
    data class UserRemoved(val success: Boolean) : GroupEvent
    data class UserMoved(val success: Boolean, val targetGroupId: String) : GroupEvent
    data class GroupRenamed(val success: Boolean, val newName: String) : GroupEvent

    data class RenameInvalid(
        val reason: com.eunilsung.talk.domain.util.GroupNameValidation
    ) : GroupEvent

    data class GroupDeleted(val success: Boolean) : GroupEvent

    data class GroupCreated(val success: Boolean, val name: String) : GroupEvent

    data class CreateInvalid(
        val reason: com.eunilsung.talk.domain.util.GroupNameValidation
    ) : GroupEvent
}

enum class GroupMode {
    IDLE, EDIT
}

package com.eunilsung.talk.ui.invite.tab.group

import com.eunilsung.talk.domain.model.Group

sealed interface InviteGroupUiState {
    val items: List<Group.Item>
    data class Idle(override val items: List<Group.Item> = emptyList()) : InviteGroupUiState
    data class Search(override val items: List<Group.Item> = emptyList()) : InviteGroupUiState
}

sealed interface InviteGroupActions {
    data class ToggleGroup(val groupId: String) : InviteGroupActions
    data class OnSearchQueryChange(val query: String) : InviteGroupActions
    data object OnClearSearch : InviteGroupActions
}

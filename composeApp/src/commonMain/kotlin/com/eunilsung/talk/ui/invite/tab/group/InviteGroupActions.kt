package com.eunilsung.talk.ui.invite.tab.group

sealed interface InviteGroupActions {
    data class ToggleGroup(val groupId: String) : InviteGroupActions
    data class OnSearchQueryChange(val query: String) : InviteGroupActions
    data object OnClearSearch : InviteGroupActions
}

package com.eunilsung.talk.ui.invite

import com.eunilsung.talk.domain.model.User


sealed interface InviteActions {
    data class Init(val mode: InviteMode) : InviteActions

    data class OnUserToggle(val user: User) : InviteActions

    data class OnUserRemove(val userId: String) : InviteActions

    data object OnSubmit : InviteActions

    data object OnClose : InviteActions

    data object Reset : InviteActions
}

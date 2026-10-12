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

sealed interface InviteEvent {
    /** 초대가 끝났다. 새로 만든 방이면 [chatRoomId] 로 그 방을 연다. */
    data class Invited(val chatRoomId: String) : InviteEvent

    data object InviteFailed : InviteEvent
}

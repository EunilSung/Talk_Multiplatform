package com.eunilsung.talk.ui.quickactions

import com.eunilsung.talk.domain.model.Group

sealed interface QuickActionsActions {
    /** 1:1 대화 시작 — 기존 방이 있으면 입장, 없으면 생성 후 입장. targetUserIds 는 1명만 유효. */
    data class StartChat(val targetUserIds: List<String>) : QuickActionsActions

    data class AddUserToGroup(val userId: String, val targetGroupId: String) : QuickActionsActions
}

sealed interface QuickActionsEvent {
    data class OpenChatRoom(val chatRoomId: String) : QuickActionsEvent

    data object ChatCreateFailed : QuickActionsEvent

    /** 그룹에 넣었다. [group] 은 넣은 뒤의 그룹이며, 목록에서 찾지 못하면 null. */
    data class AddedToGroup(val group: Group.Item?) : QuickActionsEvent

    data object AlreadyInGroup : QuickActionsEvent

    data object AddToGroupFailed : QuickActionsEvent
}

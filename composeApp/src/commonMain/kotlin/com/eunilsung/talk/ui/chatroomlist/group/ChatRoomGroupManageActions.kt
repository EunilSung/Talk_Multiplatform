package com.eunilsung.talk.ui.chatroomlist.group

sealed interface ChatRoomGroupManageActions {
    data class CreateGroup(val name: String) : ChatRoomGroupManageActions

    data class RenameGroup(val groupId: String, val newName: String) : ChatRoomGroupManageActions

    data class DeleteGroup(val groupId: String) : ChatRoomGroupManageActions

    /** 드래그로 바꾼 순서 그대로의 그룹 id 목록. */
    data class ReorderGroups(val orderedGroupIds: List<String>) : ChatRoomGroupManageActions
}

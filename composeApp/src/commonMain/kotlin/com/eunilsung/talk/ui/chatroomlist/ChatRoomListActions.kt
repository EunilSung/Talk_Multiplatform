package com.eunilsung.talk.ui.chatroomlist

import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.Search

sealed interface ChatRoomListActions {
    data class OnSearchQueryChange(val query: String) : ChatRoomListActions
    data object OnClearSearch : ChatRoomListActions
    data class SetMode(val mode: ChatRoomListMode) : ChatRoomListActions
    data class OnSelectRoom(val item: ChatRoom.Item) : ChatRoomListActions
    data class OnFilterTypeChange(val filterType: Search.FilterType) : ChatRoomListActions

    /** 그룹 칩 선택 — 해당 그룹의 대화방만 표시 (filterType=GROUP + selectedGroupId 세팅). */
    data class OnGroupFilterChange(val groupId: String) : ChatRoomListActions

    /** 대화방을 그룹에 추가 (롱클릭 메뉴 "대화방 그룹에 추가" → 그룹 선택). */
    data class OnAddRoomToGroup(val chatRoomId: String, val groupId: String) : ChatRoomListActions

    /** 대화방을 그룹에서 제거 (롱클릭 메뉴 "대화방 그룹에서 해제", 현재 필터 그룹 기준). */
    data class OnRemoveRoomFromGroup(val chatRoomId: String, val groupId: String) : ChatRoomListActions

    data object OnRefresh : ChatRoomListActions

    data class RenameChatRoom(val chatRoomId: String, val newName: String) : ChatRoomListActions

    data class SetChatRoomAlarm(val chatRoomId: String, val isAlarm: String) : ChatRoomListActions

    data class SetChatRoomPin(val chatRoomId: String, val pinned: Boolean) : ChatRoomListActions

    data class LeaveChatRoom(val chatRoomId: String) : ChatRoomListActions
}

sealed interface ChatRoomListEvent {
    data class ChatRoomRenamed(val success: Boolean, val newName: String) : ChatRoomListEvent

    data class RenameInvalid(val reason: ChatRoomNameValidation) : ChatRoomListEvent

    data class ChatRoomAlarmChanged(val success: Boolean, val muted: Boolean) : ChatRoomListEvent

    data class ChatRoomPinChanged(val success: Boolean, val pinned: Boolean) : ChatRoomListEvent

    data class ChatRoomLeft(val success: Boolean) : ChatRoomListEvent

    data class ChatRoomAddedToGroup(val groupName: String) : ChatRoomListEvent

    data object ChatRoomRemovedFromGroup : ChatRoomListEvent
}

enum class ChatRoomNameValidation {
    Valid,
    Empty,
    TooLong
}

const val CHAT_ROOM_NAME_MAX_LENGTH = 20

fun validateChatRoomName(newName: String): ChatRoomNameValidation {
    val trimmed = newName.trim()
    return when {
        trimmed.isEmpty() -> ChatRoomNameValidation.Empty
        trimmed.length > CHAT_ROOM_NAME_MAX_LENGTH -> ChatRoomNameValidation.TooLong
        else -> ChatRoomNameValidation.Valid
    }
}

enum class ChatRoomListMode {
    IDLE, EDIT
}

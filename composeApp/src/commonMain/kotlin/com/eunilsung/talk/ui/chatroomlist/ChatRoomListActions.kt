package com.eunilsung.talk.ui.chatroomlist

import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.ui.uikit.search.Search
import com.eunilsung.talk.domain.util.ChatRoomNameValidation

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

    /**
     * 편집 모드에서 고른 대화방을 한꺼번에 나간다 (하단 "대화방 나가기" → 확인).
     *
     * 나갈 방은 [chatRoomsToLeave] 가 정한다. 다 나가면 선택을 비우고 편집 모드를 끈다.
     */
    data object LeaveSelectedChatRooms : ChatRoomListActions
}

sealed interface ChatRoomListEvent {
    data class ChatRoomRenamed(val success: Boolean, val newName: String) : ChatRoomListEvent

    data class RenameInvalid(val reason: ChatRoomNameValidation) : ChatRoomListEvent

    data class ChatRoomAlarmChanged(val success: Boolean, val muted: Boolean) : ChatRoomListEvent

    data class ChatRoomPinChanged(val success: Boolean, val pinned: Boolean) : ChatRoomListEvent

    data class ChatRoomLeft(val success: Boolean) : ChatRoomListEvent

    /** 편집 모드 일괄 나가기 결과 — [left] 나간 방 수, [failed] 실패한 방 수. */
    data class ChatRoomsBulkLeft(val left: Int, val failed: Int) : ChatRoomListEvent

    data class ChatRoomAddedToGroup(val groupName: String) : ChatRoomListEvent

    data object ChatRoomRemovedFromGroup : ChatRoomListEvent

    /** 새로고침이 끝났다. 목록을 맨 위로 올린다. */
    data object ScrollToTop : ChatRoomListEvent

    /** 새 대화방이 생겼다. 맨 위 근처를 보고 있었으면 맨 위로 올린다. */
    data object NewChatRoomArrived : ChatRoomListEvent
}

enum class ChatRoomListMode {
    IDLE, EDIT
}

package com.eunilsung.talk.ui.chatroomlist

import com.eunilsung.talk.domain.model.ChatRoom

sealed interface ChatRoomListUiState {
    val chatItems: List<ChatRoom.Item>
    val isEditMode: Boolean

    data class Idle(
        override val chatItems: List<ChatRoom.Item> = emptyList(),
        override val isEditMode: Boolean = false
    ) : ChatRoomListUiState

    data class Search(
        override val chatItems: List<ChatRoom.Item> = emptyList(),
        override val isEditMode: Boolean = false
    ) : ChatRoomListUiState

    data class Unread(
        override val chatItems: List<ChatRoom.Item> = emptyList(),
        override val isEditMode: Boolean = false
    ) : ChatRoomListUiState
}

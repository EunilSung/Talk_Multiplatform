package com.eunilsung.talk.ui.share.tab

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.domain.model.ChatRoom

class ShareChatRoomController(
    val rooms: StateFlow<List<ChatRoom.Item>>,
    val selectedRoomId: String?,
    val onSelect: (ChatRoom.Item) -> Unit,
    /** 방 이름 검색어. [rooms] 는 이미 이 말로 걸러져 있다. */
    val query: String,
    val onQueryChange: (String) -> Unit,
)

val LocalShareChatRoomController = staticCompositionLocalOf<ShareChatRoomController> {
    error("LocalShareChatRoomController not provided")
}

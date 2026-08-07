package com.eunilsung.talk.ui.share.tab

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.domain.model.ChatRoom

class ShareChatRoomController(
    val rooms: StateFlow<List<ChatRoom.Item>>,
    val selectedRoomId: String?,
    val onSelect: (ChatRoom.Item) -> Unit,
)

val LocalShareChatRoomController = staticCompositionLocalOf<ShareChatRoomController> {
    error("LocalShareChatRoomController not provided")
}

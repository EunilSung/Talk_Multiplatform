package com.eunilsung.talk.ui.chatroomlist.item

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_room_leave
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.swipe.SwipeAction
import com.eunilsung.talk.ui.uikit.swipe.SwipeRevealItem
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatRoomItemList(
    pinnedRooms: List<ChatRoom.Item>,
    nonPinnedRooms: List<ChatRoom.Item>,
    listState: LazyListState,
    isEditMode: Boolean,
    onItemClick: (ChatRoom.Item) -> Unit,
    onItemLongClick: (ChatRoom.Item) -> Unit,
    onSelectRoom: (ChatRoom.Item) -> Unit,
    onLeave: (ChatRoom.Item) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val leaveLabel = stringResource(Res.string.chat_room_leave)
    LazyColumn(state = listState, modifier = modifier) {
        items(pinnedRooms, key = { "pin_${it.id}" }, contentType = { "ChatRoomItem" }) { item ->
            ChatRoomRow(item, isEditMode, onItemClick, onItemLongClick, onSelectRoom, onLeave, leaveLabel)
        }
        items(nonPinnedRooms, key = { "np_${it.id}" }, contentType = { "ChatRoomItem" }) { item ->
            ChatRoomRow(item, isEditMode, onItemClick, onItemLongClick, onSelectRoom, onLeave, leaveLabel)
        }
    }
}

@Composable
private fun LazyItemScope.ChatRoomRow(
    item: ChatRoom.Item,
    isEditMode: Boolean,
    onItemClick: (ChatRoom.Item) -> Unit,
    onItemLongClick: (ChatRoom.Item) -> Unit,
    onSelectRoom: (ChatRoom.Item) -> Unit,
    onLeave: (ChatRoom.Item) -> Unit,
    leaveLabel: String,
) {
    val row: @Composable () -> Unit = {
        ChatRoomItem(
            item = item,
            modifier = Modifier.heightIn(min = 70.dp, max = 100.dp),
            isToggleable = isEditMode,
            onClick = { if (isEditMode) onSelectRoom(item) else onItemClick(item) },
            onLongClick = { onItemLongClick(item) }
        )
    }
    if (isEditMode) {
        Box(modifier = Modifier.animateItem()) { row() }
    } else {
        SwipeRevealItem(
            modifier = Modifier.animateItem(),
            actions = listOf(
                SwipeAction(
                    label = leaveLabel,
                    background = AppColors.Red,
                    onClick = { onLeave(item) }
                )
            ),
            content = row
        )
    }
}

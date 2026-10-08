package com.eunilsung.talk.ui.chatroomlist.item.menu

import androidx.compose.runtime.Composable
import com.eunilsung.talk.ui.chatroomlist.ChatRoomListActions
import com.eunilsung.talk.ui.chatroomlist.ChatRoomListMode
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_room_add_to_group
import multiplatformtalk.composeapp.generated.resources.chat_room_alarm_off
import multiplatformtalk.composeapp.generated.resources.chat_room_alarm_on
import multiplatformtalk.composeapp.generated.resources.chat_room_leave
import multiplatformtalk.composeapp.generated.resources.chat_room_pin
import multiplatformtalk.composeapp.generated.resources.chat_room_remove_from_group
import multiplatformtalk.composeapp.generated.resources.chat_room_rename
import multiplatformtalk.composeapp.generated.resources.chat_room_unpin
import multiplatformtalk.composeapp.generated.resources.confirm_chat_room_leave
import multiplatformtalk.composeapp.generated.resources.confirm_leave_selected_chat_rooms
import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.Search
import com.eunilsung.talk.ui.uikit.dialog.DialogManager
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import org.jetbrains.compose.resources.stringResource

@Stable
class ChatRoomItemMenu internal constructor(
    private val dialogManager: DialogManager,
    private val onAction: (ChatRoomListActions) -> Unit,
    private val labels: Labels
) {
    /** 롱클릭 메뉴 — 필터별로 그룹 추가/해제 항목을 조건부 삽입. */
    fun onLongClick(
        item: ChatRoom.Item,
        filterType: Search.FilterType,
        selectedGroupId: String?,
        customGroups: List<ChatGroup>,
    ) {
        val isPinned = item.pinDate.isNotEmpty()
        val pinLabel = if (isPinned) labels.unpin else labels.pin
        val isMuted = item.isAlarm == "1"
        val alarmLabel = if (isMuted) labels.alarmOn else labels.alarmOff

        val groupLabel: String? = if (!com.eunilsung.talk.Config.ChatRoom.IS_CHAT_GROUP_ENABLED) {
            null
        } else when (filterType) {
            Search.FilterType.ALL -> labels.addToGroup
            Search.FilterType.GROUP -> labels.removeFromGroup
            else -> null
        }

        val menuItems = buildList {
            add(labels.rename)
            add(pinLabel)
            add(alarmLabel)
            if (groupLabel != null) add(groupLabel)
            add(labels.leave)
        }

        dialogManager.list(
            title = item.displayTitle,
            items = menuItems,
            destructiveItems = setOf(labels.leave),
            submenuItems = if (groupLabel == labels.addToGroup) setOf(labels.addToGroup) else emptySet(),
            onSelected = { picked ->
                when (picked) {
                    labels.rename     -> dialogManager.textInput(
                        title = labels.rename,
                        initialValue = item.title,
                        hint = item.title,
                        onConfirm = { rawInput ->
                            onAction(
                                ChatRoomListActions.RenameChatRoom(
                                    chatRoomId = item.id,
                                    newName = rawInput
                                )
                            )
                        }
                    )
                    pinLabel          -> onAction(
                        ChatRoomListActions.SetChatRoomPin(
                            chatRoomId = item.id,
                            pinned = !isPinned
                        )
                    )
                    alarmLabel        -> {
                        val newValue = if (isMuted) "0" else "1"
                        onAction(
                            ChatRoomListActions.SetChatRoomAlarm(
                                chatRoomId = item.id,
                                isAlarm = newValue
                            )
                        )
                    }
                    labels.addToGroup -> showGroupPicker(item, customGroups)
                    labels.removeFromGroup -> {
                        if (!selectedGroupId.isNullOrBlank()) {
                            onAction(
                                ChatRoomListActions.OnRemoveRoomFromGroup(
                                    chatRoomId = item.id,
                                    groupId = selectedGroupId
                                )
                            )
                        }
                    }
                    labels.leave      -> onLeaveClick(item)
                }
            }
        )
    }

    /** "대화방 그룹에 추가" 하위메뉴 — 커스텀 그룹 목록에서 선택 → [ChatRoomListActions.OnAddRoomToGroup]. */
    private fun showGroupPicker(item: ChatRoom.Item, customGroups: List<ChatGroup>) {
        if (customGroups.isEmpty()) return
        dialogManager.list(
            title = labels.addToGroup,
            items = customGroups.map { it.name },
            onSelected = { pickedName ->
                customGroups.firstOrNull { it.name == pickedName }?.let { group ->
                    onAction(
                        ChatRoomListActions.OnAddRoomToGroup(
                            chatRoomId = item.id,
                            groupId = group.id
                        )
                    )
                }
            }
        )
    }

    /** 스와이프 나가기 — 확인 다이얼로그 후 나가기 (롱클릭 메뉴의 나가기와 동일 플로우). */
    fun onLeaveClick(item: ChatRoom.Item) {
        dialogManager.confirm(
            title = labels.leave,
            message = labels.confirmLeave,
            confirmText = labels.leave,
            onConfirm = {
                onAction(ChatRoomListActions.LeaveChatRoom(item.id))
            }
        )
    }

    /** 편집 모드 하단 나가기 — 고른 방이 없으면 편집 모드만 끄고, 있으면 확인 후 한꺼번에 나간다. */
    fun onLeaveSelectedClick(hasSelection: Boolean) {
        if (!hasSelection) {
            onAction(ChatRoomListActions.SetMode(ChatRoomListMode.IDLE))
            return
        }
        dialogManager.confirm(
            title = labels.leave,
            message = labels.confirmLeaveSelected,
            confirmText = labels.leave,
            onConfirm = {
                onAction(ChatRoomListActions.LeaveSelectedChatRooms)
            }
        )
    }

    @Stable
    data class Labels(
        val rename: String,
        val pin: String,
        val unpin: String,
        val alarmOff: String,
        val alarmOn: String,
        val leave: String,
        val confirmLeave: String,
        val confirmLeaveSelected: String,
        val addToGroup: String,
        val removeFromGroup: String
    )
}

@Composable
fun rememberChatRoomItemMenu(
    onAction: (ChatRoomListActions) -> Unit
): ChatRoomItemMenu {
    val dialogManager = LocalDialogManager.current
    val labels = ChatRoomItemMenu.Labels(
        rename = stringResource(Res.string.chat_room_rename),
        pin = stringResource(Res.string.chat_room_pin),
        unpin = stringResource(Res.string.chat_room_unpin),
        alarmOff = stringResource(Res.string.chat_room_alarm_off),
        alarmOn = stringResource(Res.string.chat_room_alarm_on),
        leave = stringResource(Res.string.chat_room_leave),
        confirmLeave = stringResource(Res.string.confirm_chat_room_leave),
        confirmLeaveSelected = stringResource(Res.string.confirm_leave_selected_chat_rooms),
        addToGroup = stringResource(Res.string.chat_room_add_to_group),
        removeFromGroup = stringResource(Res.string.chat_room_remove_from_group)
    )
    return remember(dialogManager, onAction, labels) {
        ChatRoomItemMenu(dialogManager, onAction, labels)
    }
}

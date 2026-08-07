package com.eunilsung.talk.ui.chatroom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.chatroom.empathy.EmpathyUsersBottomSheetContent
import com.eunilsung.talk.ui.chatroom.item.ChatItem
import com.eunilsung.talk.ui.chatroom.item.DateHeaderItem
import com.eunilsung.talk.ui.chatroom.vote.VoteMode
import com.eunilsung.talk.ui.chatroom.vote.VoteScreen
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import com.eunilsung.talk.ui.uikit.mediapicker.LocalShowFileDetail
import com.eunilsung.talk.ui.uikit.mediapicker.MultimediaRecentPhoto
import com.eunilsung.talk.ui.uikit.sheet.LocalBottomSheetManager
import com.eunilsung.talk.ui.userprofile.LocalShowUserProfile
import com.eunilsung.talk.ui.util.chatDateHeaderText
import com.eunilsung.talk.ui.util.formatChatTime
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.delete


/**
 * 대화 목록 — 날짜 구분선·안읽음 마커·말풍선을 그리고, 포커스된 대화를 잠깐 흔든다.
 *
 * 흔들기 중복 방지 키([lastShakenFocusKey])는 화면이 소유한다. 목록이 재구성돼도
 * 같은 검색 결과를 다시 흔들지 않으려면 이 컴포저블 밖에서 살아남아야 한다.
 */
@Composable
fun ChatMessageList(
    groupedChats: List<com.eunilsung.talk.domain.model.GroupedChat>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    searchState: ChatSearchState,
    chatFontSize: androidx.compose.ui.unit.TextUnit,
    bookmarkedChatIds: Set<String>,
    currentChatRoomId: String,
    strings: ChatRoomStrings,
    lastShakenFocusKey: Int,
    onShakenFocusKeyChange: (Int) -> Unit,
    mediaPicker: com.eunilsung.talk.ui.uikit.mediapicker.MediaPickerState,
    hideKeyboard: () -> Unit,
    users: List<com.eunilsung.talk.domain.model.User>,
    onLongPress: (Chat.Item) -> Unit,
    onNoticeClick: (Chat.Item) -> Unit,
    onAction: (ChatRoomActions) -> Unit,
) {
    val dialog = com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager.current
    val bottomSheet = com.eunilsung.talk.ui.uikit.sheet.LocalBottomSheetManager.current
    val fullScreenOverlay = com.eunilsung.talk.ui.main.LocalFullScreenOverlay.current
    val showUserProfileFromContent = com.eunilsung.talk.ui.userprofile.LocalShowUserProfile.current
    val showFileDetail = com.eunilsung.talk.ui.uikit.mediapicker.LocalShowFileDetail.current

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(AppColors.ChatRoomBg)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { hideKeyboard() })
            }
    ) {
        items(
            items = groupedChats,
            key = { it.chat.chatID },
            // 한 목록에 말풍선 종류가 10가지라 지정하지 않으면 슬롯 재사용이 무효화된다.
            contentType = { it.chat.chatType },
        ) { grouped ->
            val chat = grouped.chat
            val shakeOffset = remember { Animatable(0f) }
            LaunchedEffect(grouped.isFocused, searchState.focusTriggerKey) {
                if (!grouped.isFocused) return@LaunchedEffect
                if (searchState.focusTriggerKey == lastShakenFocusKey) return@LaunchedEffect
                onShakenFocusKeyChange(searchState.focusTriggerKey)
                val amps = listOf(8f, -8f, 6f, -6f, 4f, -4f, 2f, -2f, 0f)
                amps.forEach { a ->
                    shakeOffset.animateTo(a, animationSpec = tween(60))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(shakeOffset.value.dp.roundToPx(), 0) }
                    .padding(vertical = 4.dp)
            ) {
                if (grouped.showDate) {
                    DateHeaderItem(
                        dateText = chatDateHeaderText(chat.date)
                    )
                }
                if (grouped.showUnreadMarker) {
                    UnreadMarker()
                }

                ChatItem(
                    groupedItem = grouped,
                    searchQuery = searchState.committedQuery,
                    searchedUserName = searchState.searchedUser?.name.orEmpty(),
                    chatFontSize = chatFontSize,
                    isBookmarked = bookmarkedChatIds.contains(grouped.chat.chatID),
                    onSwipeReply = {
                        if (Config.ChatRoom.IS_REPLY_ENABLED) {
                            onAction(ChatRoomActions.OnSwipeReply(it))
                        }
                                   },
                    onReplyOriginClick = { originId ->
                        hideKeyboard()
                        onAction(ChatRoomActions.OnFocusChat(originId))
                    },
                    onImageClick = { item ->
                        hideKeyboard()
                        mediaPicker.showDetail(
                            MultimediaRecentPhoto(
                                id = item.chatID,
                                uri = item.imagePath,
                                serverFileName = item.imagePath,
                                originalFileName = item.originalFileName,
                                isVideo = item.chatType == Chat.Type.VIDEO,
                            )
                        )
                    },
                    onResendFailedChat = { item ->
                        dialog.confirm(
                            title = strings.resendTitle,
                            message = strings.resendMessage,
                            confirmText = strings.resend,
                            dismissText = strings.cancel,
                            onConfirm = {
                                onAction(ChatRoomActions.OnResendFailedChat(item.chatID))
                            },
                        )
                    },
                    onDeleteFailedChat = { item ->
                        dialog.confirm(
                            title = strings.deleteTitle,
                            message = strings.deleteMessage,
                            confirmText = strings.delete,
                            dismissText = strings.cancel,
                            onConfirm = {
                                onAction(ChatRoomActions.OnDeleteFailedChat(item.chatID))
                            },
                        )
                    },
                    onLongClick = { item ->
                        onLongPress(item)
                    },
                    onVoteClick = { item ->
                        hideKeyboard()
                        val isComplete = item.chatType == Chat.Type.VOTE_COMPLETE
                        val voteId = item.vote?.id?.takeIf { it.isNotBlank() }
                            ?: item.voteComplete?.id.orEmpty()
                        fullScreenOverlay(
                            VoteScreen(
                                chatRoomId = currentChatRoomId,
                                initialMode = if (isComplete) VoteMode.RESULT else VoteMode.PARTICIPATE,
                                voteId = voteId,
                            )
                        )
                    },
                    onNoticeClick = { item ->
                        hideKeyboard()
                        onNoticeClick(item)
                    },
                    onEmpathyClick = { chatItem, typeIndex ->
                        onAction(
                            ChatRoomActions.OnSendEmpathy(
                                targetChatId = chatItem.chatID,
                                empathyType = typeIndex.toString(),
                            )
                        )
                    },
                    onEmpathyLongClick = { chatItem, typeIndex ->
                        hideKeyboard()
                        bottomSheet.custom {
                            EmpathyUsersBottomSheetContent(
                                empathy = chatItem.empathy,
                                selectedType = typeIndex,
                                resolveUser = { id -> users.find { it.id == id } },
                                onUserClick = { user ->
                                    bottomSheet.hide()
                                    showUserProfileFromContent(user.id)
                                },
                            )
                        }
                    },
                    onFileClick = { item ->
                        hideKeyboard()
                        val sender = buildString {
                            if (item.user.name.isNotBlank()) append(item.user.name)
                            val itemTime = formatChatTime(item.date, strings.am, strings.pm)
                            if (itemTime.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(itemTime)
                            }
                        }
                        showFileDetail(
                            item.imagePath,
                            item.originalFileName,
                            sender,
                        )
                    },
                )
            }
        }
    }
}

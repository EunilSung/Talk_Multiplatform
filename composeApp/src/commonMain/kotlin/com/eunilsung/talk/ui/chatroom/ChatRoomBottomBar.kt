package com.eunilsung.talk.ui.chatroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.ui.chatroom.input.ChatInputBar
import com.eunilsung.talk.ui.chatroom.input.MentionFieldState
import com.eunilsung.talk.ui.chatroom.input.ReplyPreviewBar
import com.eunilsung.talk.ui.main.KeyBoardPane
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.emoticon.EmoticonItem
import com.eunilsung.talk.ui.uikit.emoticon.EmoticonPaneContent
import com.eunilsung.talk.ui.uikit.emoticon.EmoticonPanelController
import com.eunilsung.talk.ui.uikit.line.LineDivider

/**
 * 대화방 하단바 — 답장 미리보기 / 입력창+이모티콘 / 검색 모드의 이동 네비게이션.
 *
 * 이모티콘 탭·선택은 화면이 소유한 상태라 값과 갱신 콜백을 함께 받는다.
 */
@Composable
fun ChatRoomBottomBar(
    isSearchMode: Boolean,
    searchState: ChatSearchState,
    replyTarget: com.eunilsung.talk.domain.model.Chat.Item?,
    mentionState: com.eunilsung.talk.ui.chatroom.input.MentionFieldState,
    emoticon: com.eunilsung.talk.ui.uikit.emoticon.EmoticonPanelController,
    selectedEmoticon: com.eunilsung.talk.ui.uikit.emoticon.EmoticonItem?,
    onSelectedEmoticonChange: (com.eunilsung.talk.ui.uikit.emoticon.EmoticonItem?) -> Unit,
    lastEmoticonTab: Int,
    onEmoticonTabChange: (Int) -> Unit,
    onEmoticonTabSelected: (Int) -> Unit,
    enterToSend: Boolean,
    isKeyboardVisible: Boolean,
    imeHeight: androidx.compose.ui.unit.Dp,
    panelHeight: androidx.compose.ui.unit.Dp,
    mediaPicker: com.eunilsung.talk.ui.uikit.mediapicker.MediaPickerState,
    chatInputFocusRequester: androidx.compose.ui.focus.FocusRequester,
    chatFontSize: androidx.compose.ui.unit.TextUnit,
    isSearchUserBoxVisible: Boolean,
    onSearchUserBoxToggle: () -> Unit,
    isSearchDateBoxVisible: Boolean,
    onSearchDateBoxToggle: () -> Unit,
    hideKeyboard: () -> Unit,
    onAction: (ChatRoomActions) -> Unit,
) {
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            Column {
                LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
                if (!isSearchMode && replyTarget != null) {
                    ReplyPreviewBar(
                        target = replyTarget,
                        onCancel = { onAction(ChatRoomActions.OnCancelReply) }
                    )
                }
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.BgSub)
                    .heightIn(min = 70.dp),
                    contentAlignment = Alignment.Center
                ){
                    if (!isSearchMode) {

                        ChatInputBar(
                            value = mentionState.value,
                            onValueChange = { mentionState.onValueChange(it) },
                            visualTransformation = mentionState.visualTransformation(AppColors.SkyLine),
                            onSendClick = {
                                onAction(ChatRoomActions.OnSendText(mentionState.toTaggedText(), emoticonId = selectedEmoticon?.id))
                                mentionState.clear()
                                onSelectedEmoticonChange(null)
                            },
                            onMultimediaClick = {
                                hideKeyboard()
                                mediaPicker.open()
                                                },
                            onEmoticonClick = {
                                if (emoticon.isOpen) {
                                    emoticon.close()
                                } else {
                                    keyboardController?.hide()
                                    emoticon.open {
                                        EmoticonPaneContent(
                                            initialTab = lastEmoticonTab,
                                            onTabSelected = {
                                                onEmoticonTabChange(it)
                                                onEmoticonTabSelected(it)
                                            },
                                            onEmoticonSelected = {
                                                onSelectedEmoticonChange(it)
                                                if (emoticon.isExpanded) emoticon.collapse()
                                            },
                                            onEmoticonDoubleClick = { item ->
                                                onAction(ChatRoomActions.OnSendText(mentionState.toTaggedText(), emoticonId = item.id))
                                                mentionState.clear()
                                                onSelectedEmoticonChange(null)
                                            },
                                        )
                                    }
                                }
                            },
                            enterToSend = enterToSend,
                            showMultimedia = Config.ChatRoom.IS_MULTIMEDIA_ENABLED && replyTarget == null,
                            showEmoticon = Config.ChatRoom.IS_EMOTICON_ENABLED,
                            hasEmoticon = selectedEmoticon != null,
                            focusRequester = chatInputFocusRequester,
                            fontSize = chatFontSize,
                        )
                    }else{
                        ChatSearchNav(
                            searchState = searchState,
                            onUserClick = {
                                onSearchUserBoxToggle()
                            },
                            onDateClick = {
                                onSearchDateBoxToggle()
                            },
                            onPrevClick = { onAction(ChatRoomActions.OnSearchPrev) },
                            onNextClick = { onAction(ChatRoomActions.OnSearchNext) }
                        )
                    }
                }
                KeyBoardPane(
                    modifier = Modifier.background(AppColors.BgSub),
                    isKeyboardVisible = isKeyboardVisible,
                    imeHeight = imeHeight,
                    emoticon = emoticon,
                    panelHeight = panelHeight,
                        fillIdleBottomInset = true,
                )
    }
}

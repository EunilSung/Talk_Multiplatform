package com.eunilsung.talk.ui.chatroom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.isForwardable
import com.eunilsung.talk.ui.uikit.dialog.ChatListDialog
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import com.eunilsung.talk.ui.uikit.TestTags

/**
 * 대화 롱프레스 메뉴 — 복사·답장·전달·번역·공지등록·책갈피·회수 + 공감 선택.
 *
 * 표시할 항목은 대화 종류와 기능 플래그로 정해지고, 회수만 확인 다이얼로그를 한 번 더 띄운다.
 *
 * @param target 롱프레스한 대화. null 이면 메뉴를 그리지 않는다.
 * @param isBookmarked 책갈피 항목의 문구(설정/해제)를 고르는 데 쓴다.
 * @param isTranslated 번역 항목의 문구(번역/번역 숨기기)를 고르는 데 쓴다.
 * @param onForward "전달" — 받을 곳을 고르는 화면을 연다. 전달할 수 있는 대화에만 보인다.
 */
@Composable
fun ChatActionMenu(
    target: Chat.Item?,
    isBookmarked: Boolean,
    isTranslated: Boolean,
    strings: ChatRoomStrings,
    onDismiss: () -> Unit,
    onForward: (Chat.Item) -> Unit,
    onAction: (ChatRoomActions) -> Unit,
) {
    if (target == null) return

    val dialog = LocalDialogManager.current
    val clipboard = LocalClipboardManager.current

    val isTextLike = target.chatType == Chat.Type.TEXT || target.chatType == Chat.Type.REPLY
    val bookmarkLabel = if (isBookmarked) strings.bookmarkUnset else strings.bookmarkSet
    val translateLabel = if (isTranslated) strings.menuTranslateHide else strings.menuTranslate
    val languageCode = Locale.current.language
    val actionItems = buildList {
        if (Config.ChatRoom.IS_COPY_ENABLED && isTextLike) add(strings.menuCopy)
        if (Config.ChatRoom.IS_REPLY_ENABLED && !target.isRecalled) add(strings.menuReply)
        if (Config.ChatRoom.IS_FORWARD_ENABLED && target.isForwardable) add(strings.menuForward)
        if (Config.Server.IS_ENABLED && isTextLike && !target.isRecalled) add(translateLabel)
        if (Config.ChatRoom.IS_NOTICE_ENABLED && isTextLike) add(strings.menuNotice)
        if (Config.ChatRoom.IS_BOOK_MARK_ENABLED) add(bookmarkLabel)
        if (Config.ChatRoom.IS_RECALL_ENABLED && target.isMe) add(strings.menuRecall)
    }
    if (actionItems.isEmpty() && !Config.ChatRoom.IS_EMPATHY_ENABLED) {
        LaunchedEffect(target.chatID) { onDismiss() }
        return
    }

    ChatListDialog(
        list = actionItems,
        destructiveItems = setOf(strings.menuRecall),
        showEmpathy = Config.ChatRoom.IS_EMPATHY_ENABLED,
        optionTags = mapOf(
            strings.menuTranslate to TestTags.CHAT_MENU_TRANSLATE,
            strings.menuForward to TestTags.CHAT_MENU_FORWARD,
        ),
        onDismiss = onDismiss,
        onOptionSelected = { picked ->
            onDismiss()
            when (picked) {
                strings.menuCopy -> clipboard.setText(AnnotatedString(target.chatContent))
                strings.menuReply -> onAction(ChatRoomActions.OnSwipeReply(target))
                strings.menuForward -> onForward(target)
                strings.menuTranslate -> onAction(ChatRoomActions.OnTranslateChat(target.chatID, languageCode))
                strings.menuTranslateHide -> onAction(ChatRoomActions.OnHideTranslation(target.chatID))
                strings.menuNotice -> target.chatContent.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { onAction(ChatRoomActions.OnSubmitNotice(it)) }
                strings.bookmarkSet -> onAction(ChatRoomActions.OnAddBookmark(target))
                strings.bookmarkUnset -> onAction(ChatRoomActions.OnDeleteBookmark(target.chatID))
                strings.menuRecall -> dialog.confirm(
                    title = strings.recallTitle,
                    message = strings.recallMessage,
                    confirmText = strings.menuRecall,
                    dismissText = strings.cancel,
                    onConfirm = { onAction(ChatRoomActions.OnRecallChat(target.chatID)) },
                )
            }
        },
        onEmpathyClick = { typeIndex ->
            onDismiss()
            onAction(
                ChatRoomActions.OnSendEmpathy(
                    targetChatId = target.chatID,
                    empathyType = typeIndex.toString(),
                )
            )
        },
    )
}

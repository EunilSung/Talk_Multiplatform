package com.eunilsung.talk.ui.chatroom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.chat_bookmark_set
import multiplatformtalk.composeapp.generated.resources.chat_bookmark_unset
import multiplatformtalk.composeapp.generated.resources.chat_delete_message
import multiplatformtalk.composeapp.generated.resources.chat_delete_title
import multiplatformtalk.composeapp.generated.resources.chat_menu_copy
import multiplatformtalk.composeapp.generated.resources.chat_menu_notice
import multiplatformtalk.composeapp.generated.resources.chat_menu_recall
import multiplatformtalk.composeapp.generated.resources.chat_menu_reply
import multiplatformtalk.composeapp.generated.resources.chat_menu_translate
import multiplatformtalk.composeapp.generated.resources.chat_menu_translate_hide
import multiplatformtalk.composeapp.generated.resources.chat_polish_concise
import multiplatformtalk.composeapp.generated.resources.chat_polish_correct
import multiplatformtalk.composeapp.generated.resources.chat_polish_polite
import multiplatformtalk.composeapp.generated.resources.chat_recall_message
import multiplatformtalk.composeapp.generated.resources.chat_recall_title
import multiplatformtalk.composeapp.generated.resources.chat_resend
import multiplatformtalk.composeapp.generated.resources.chat_resend_message
import multiplatformtalk.composeapp.generated.resources.chat_resend_title
import multiplatformtalk.composeapp.generated.resources.date_ampm_am
import multiplatformtalk.composeapp.generated.resources.date_ampm_pm
import multiplatformtalk.composeapp.generated.resources.delete
import multiplatformtalk.composeapp.generated.resources.notice_delete_message
import multiplatformtalk.composeapp.generated.resources.notice_delete_title
import multiplatformtalk.composeapp.generated.resources.toast_no_notice
import multiplatformtalk.composeapp.generated.resources.toast_polish_unavailable
import multiplatformtalk.composeapp.generated.resources.toast_translate_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * 대화방 화면이 쓰는 문자열 묶음.
 *
 * `stringResource` 는 `@Composable` 이라 다이얼로그 콜백·롱프레스 메뉴처럼 컴포지션 밖에서
 * 필요한 문구를 미리 읽어 둬야 한다. 화면 상단에 20줄 넘게 늘어놓는 대신 한 곳에 모은다.
 */
@Immutable
data class ChatRoomStrings(
    val cancel: String,
    val delete: String,
    val am: String,
    val pm: String,
    val resendTitle: String,
    val resendMessage: String,
    val resend: String,
    val deleteTitle: String,
    val deleteMessage: String,
    val recallTitle: String,
    val recallMessage: String,
    val noticeDeleteTitle: String,
    val noticeDeleteMessage: String,
    val noNotice: String,
    val menuCopy: String,
    val menuReply: String,
    val menuNotice: String,
    val menuRecall: String,
    val menuTranslate: String,
    val menuTranslateHide: String,
    val translateUnavailable: String,
    val polishCorrect: String,
    val polishPolite: String,
    val polishConcise: String,
    val polishUnavailable: String,
    val bookmarkSet: String,
    val bookmarkUnset: String,
)

@Composable
fun rememberChatRoomStrings(): ChatRoomStrings {
    val cancel = stringResource(Res.string.cancel)
    val delete = stringResource(Res.string.delete)
    val am = stringResource(Res.string.date_ampm_am)
    val pm = stringResource(Res.string.date_ampm_pm)
    val resendTitle = stringResource(Res.string.chat_resend_title)
    val resendMessage = stringResource(Res.string.chat_resend_message)
    val resend = stringResource(Res.string.chat_resend)
    val deleteTitle = stringResource(Res.string.chat_delete_title)
    val deleteMessage = stringResource(Res.string.chat_delete_message)
    val recallTitle = stringResource(Res.string.chat_recall_title)
    val recallMessage = stringResource(Res.string.chat_recall_message)
    val noticeDeleteTitle = stringResource(Res.string.notice_delete_title)
    val noticeDeleteMessage = stringResource(Res.string.notice_delete_message)
    val noNotice = stringResource(Res.string.toast_no_notice)
    val menuCopy = stringResource(Res.string.chat_menu_copy)
    val menuReply = stringResource(Res.string.chat_menu_reply)
    val menuNotice = stringResource(Res.string.chat_menu_notice)
    val menuRecall = stringResource(Res.string.chat_menu_recall)
    val menuTranslate = stringResource(Res.string.chat_menu_translate)
    val menuTranslateHide = stringResource(Res.string.chat_menu_translate_hide)
    val translateUnavailable = stringResource(Res.string.toast_translate_unavailable)
    val polishCorrect = stringResource(Res.string.chat_polish_correct)
    val polishPolite = stringResource(Res.string.chat_polish_polite)
    val polishConcise = stringResource(Res.string.chat_polish_concise)
    val polishUnavailable = stringResource(Res.string.toast_polish_unavailable)
    val bookmarkSet = stringResource(Res.string.chat_bookmark_set)
    val bookmarkUnset = stringResource(Res.string.chat_bookmark_unset)
    return remember(cancel, delete, am, pm, menuCopy, bookmarkSet) {
        ChatRoomStrings(
            cancel = cancel,
            delete = delete,
            am = am,
            pm = pm,
            resendTitle = resendTitle,
            resendMessage = resendMessage,
            resend = resend,
            deleteTitle = deleteTitle,
            deleteMessage = deleteMessage,
            recallTitle = recallTitle,
            recallMessage = recallMessage,
            noticeDeleteTitle = noticeDeleteTitle,
            noticeDeleteMessage = noticeDeleteMessage,
            noNotice = noNotice,
            menuCopy = menuCopy,
            menuReply = menuReply,
            menuNotice = menuNotice,
            menuRecall = menuRecall,
            menuTranslate = menuTranslate,
            menuTranslateHide = menuTranslateHide,
            translateUnavailable = translateUnavailable,
            polishCorrect = polishCorrect,
            polishPolite = polishPolite,
            polishConcise = polishConcise,
            polishUnavailable = polishUnavailable,
            bookmarkSet = bookmarkSet,
            bookmarkUnset = bookmarkUnset,
        )
    }
}

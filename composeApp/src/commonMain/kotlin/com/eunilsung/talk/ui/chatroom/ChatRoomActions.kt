package com.eunilsung.talk.ui.chatroom

import com.eunilsung.talk.ui.chatroomlist.ChatRoomNameValidation

sealed interface ChatRoomActions {
    data class Load(val chatRoomId: String) : ChatRoomActions

    data object OnClose : ChatRoomActions

    data object OnReachEnd : ChatRoomActions

    data object OnReachStart : ChatRoomActions

    data object OnEnterSearch : ChatRoomActions

    data object OnExitSearch : ChatRoomActions

    data class OnSearchQueryChange(val query: String) : ChatRoomActions

    data object OnSearch : ChatRoomActions

    data object OnSearchNext : ChatRoomActions

    data object OnSearchPrev : ChatRoomActions

    data class OnSearchUserSelect(val user: com.eunilsung.talk.domain.model.User) : ChatRoomActions

    /** 전송 주체 변경(샘플 전용). `null` 이면 나로 되돌린다. */
    data class OnSelectSender(val user: com.eunilsung.talk.domain.model.User?) : ChatRoomActions

    data object OnSearchUserRemove : ChatRoomActions

    data class OnSearchByDate(val datePrefix: String) : ChatRoomActions

    data class OnSwipeReply(val chat: com.eunilsung.talk.domain.model.Chat.Item) : ChatRoomActions

    data object OnCancelReply : ChatRoomActions

    data class OnSendText(
        val text: String,
        val emoticonId: String? = null,
    ) : ChatRoomActions

    data class OnResendFailedChat(val chatId: String) : ChatRoomActions

    data class OnDeleteFailedChat(val chatId: String) : ChatRoomActions

    data class OnSendEmpathy(val targetChatId: String, val empathyType: String) : ChatRoomActions

    data class OnRecallChat(val targetChatId: String) : ChatRoomActions

    data object OnMoveToLatest : ChatRoomActions

    data class OnSendFiles(val paths: List<String>) : ChatRoomActions

    data class OnSubmitNotice(val content: String) : ChatRoomActions

    data object OnDeleteNotice : ChatRoomActions

    data class OnFocusChat(val chatId: String) : ChatRoomActions

    data class OnAddBookmark(val chat: com.eunilsung.talk.domain.model.Chat.Item) : ChatRoomActions

    data class OnDeleteBookmark(val chatId: String) : ChatRoomActions

    /** [languageCode] 는 기기 언어(`ko`·`en` …) — 그 언어로 번역해 달라고 한다. */
    data class OnTranslateChat(val chatId: String, val languageCode: String) : ChatRoomActions

    data class OnHideTranslation(val chatId: String) : ChatRoomActions

    data class OnPolishText(val text: String, val style: com.eunilsung.talk.domain.model.PolishStyle) : ChatRoomActions

    /** 화면이 다듬기 결과를 입력창에 반영했거나 실패 안내를 띄웠다. */
    data object OnPolishHandled : ChatRoomActions
}



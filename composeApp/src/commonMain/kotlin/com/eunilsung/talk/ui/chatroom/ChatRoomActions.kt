package com.eunilsung.talk.ui.chatroom

import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.PolishStyle
import com.eunilsung.talk.domain.model.User

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

    data class OnSearchUserSelect(val user: User) : ChatRoomActions

    /** 전송 주체 변경(샘플 전용). `null` 이면 나로 되돌린다. */
    data class OnSelectSender(val user: User?) : ChatRoomActions

    data object OnSearchUserRemove : ChatRoomActions

    data class OnSearchByDate(val datePrefix: String) : ChatRoomActions

    data class OnSwipeReply(val chat: Chat.Item) : ChatRoomActions

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

    data class OnAddBookmark(val chat: Chat.Item) : ChatRoomActions

    data class OnDeleteBookmark(val chatId: String) : ChatRoomActions

    /** [languageCode] 는 기기 언어(`ko`·`en` …) — 그 언어로 번역해 달라고 한다. */
    data class OnTranslateChat(val chatId: String, val languageCode: String) : ChatRoomActions

    data class OnHideTranslation(val chatId: String) : ChatRoomActions

    /** 파일 대화를 AI Agent 앱에서 요약하게 한다. */
    data class OnRequestAiSummary(val chat: Chat.Item) : ChatRoomActions

    data class OnPolishText(val text: String, val style: PolishStyle) : ChatRoomActions

    /** 화면이 다듬기 결과를 입력창에 반영했거나 실패 안내를 띄웠다. */
    data object OnPolishHandled : ChatRoomActions

    /** 화면에 이 방을 띄웠다. 이 방의 알림을 걷고, 이 방의 새 대화를 읽은 것으로 치게 한다. */
    data class OnEnterRoom(val chatRoomId: String) : ChatRoomActions

    /** 화면에서 이 방을 내렸다. 이 방에 딸려 들고 있던 공지·책갈피도 비운다. */
    data class OnLeaveRoom(val chatRoomId: String) : ChatRoomActions

    /** 앱이 다시 앞으로 나왔다. 뒤에 있는 동안 쌓인 이 방의 알림을 걷는다. */
    data class OnResumeRoom(val chatRoomId: String) : ChatRoomActions

    /** 공지가 바뀌었다. 그 공지에 기억해 둔 공지바 표시 상태를 읽어 온다. */
    data class OnLoadNoticeBar(val identityKey: String) : ChatRoomActions

    data class OnNoticeExpandedChange(val identityKey: String, val expanded: Boolean) : ChatRoomActions

    data class OnNoticeDetailsChange(val identityKey: String, val shown: Boolean) : ChatRoomActions

    /** 이 공지는 공지바에 다시 띄우지 않는다. */
    data class OnHideNotice(val identityKey: String) : ChatRoomActions

    /** 사진 시트를 열었다 — 최근 사진을 읽는다. */
    data object OnLoadRecentPhotos : ChatRoomActions

    /** 갤러리 시트를 열었다 — 고른 앨범의 사진을 읽는다. */
    data object OnLoadGalleryPhotos : ChatRoomActions

    /** 앨범을 골랐다 — 그 폴더의 사진만 다시 읽는다. null 이면 전체. */
    data class OnSelectGalleryAlbum(val albumId: String?) : ChatRoomActions

    data object OnRequestPhotoAccess : ChatRoomActions

    /** 파일 고르기 창을 띄운다. 고른 파일은 이 방으로 보낸다. */
    data object OnLaunchFilePicker : ChatRoomActions

    data class OnEmoticonTabSelected(val index: Int) : ChatRoomActions
}

sealed interface ChatRoomEvent {
    /** 내가 이 방에서 나가졌다. 화면을 닫는다. */
    data object SelfLeft : ChatRoomEvent

    /** 번역을 받지 못했다. 안내를 한 번 띄운다. */
    data object TranslationFailed : ChatRoomEvent

    /** 파일을 아직 기기에 받지 못해 AI 요약을 맡기지 못했다. 잠시 뒤 다시 하라고 안내한다. */
    data object AiSummaryFileNotReady : ChatRoomEvent

    /** AI Agent 앱이 기기에 없어 AI 요약을 맡기지 못했다. */
    data object AiAgentNotInstalled : ChatRoomEvent
}



package com.eunilsung.talk.ui.chatroom.media

sealed interface ChatMediaActions {
    /** [chatRoomId] 방의 사진·동영상·파일을 기기 DB 에서 읽는다. 화면이 열릴 때. */
    data class Load(val chatRoomId: String) : ChatMediaActions

    data class OnTabChange(val tab: ChatMediaTab) : ChatMediaActions

    /** 파일 상세 시트의 "다운로드". */
    data class OnDownloadFile(val source: String, val displayName: String) : ChatMediaActions

    /** 파일 상세 시트의 "열기". */
    data class OnOpenFile(val source: String, val displayName: String) : ChatMediaActions
}

/** 모아보기가 한 번씩 알리는 일 — 파일 받기·열기의 결과. */
sealed interface ChatMediaEvent {
    data class FileDownloaded(val displayName: String) : ChatMediaEvent

    data object FileDownloadFailed : ChatMediaEvent

    data object FileOpenFailed : ChatMediaEvent
}

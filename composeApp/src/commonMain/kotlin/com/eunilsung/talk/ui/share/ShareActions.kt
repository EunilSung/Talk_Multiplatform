package com.eunilsung.talk.ui.share

import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.model.SharedContent
import com.eunilsung.talk.domain.model.ChatRoom

sealed interface ShareActions {
    data class Init(val content: SharedContent) : ShareActions

    /** 대화방 롱클릭 "전달"로 열었다 — 고른 곳에 [sourceRoomId] 방의 [chat] 을 전달한다. */
    data class InitForward(val chat: Chat.Item, val sourceRoomId: String) : ShareActions

    data class OnUserToggle(val user: User) : ShareActions

    data class OnUserRemove(val userId: String) : ShareActions

    data class OnChatRoomSelect(val chatRoom: ChatRoom.Item) : ShareActions

    data object OnChatRoomClear : ShareActions

    data class OnRoomQueryChange(val query: String) : ShareActions

    data object OnSubmit : ShareActions

    data object OnClose : ShareActions

    data object Reset : ShareActions
}

sealed interface ShareEvent {
    /** 보낼 방이 정해졌다. 공유 화면을 닫고 그 방을 연다. */
    data class OpenChatRoom(val chatRoomId: String) : ShareEvent

    data object SendFailed : ShareEvent
}

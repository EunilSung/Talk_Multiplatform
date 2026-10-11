package com.eunilsung.talk.ui.chatroom

import androidx.compose.runtime.Immutable
import com.eunilsung.talk.domain.model.Chat

/** 화면 렌더링 단위 — 대화 하나에 묶음·표시선·검색 강조 같은 화면 판단을 얹은 것. */
@Immutable
data class GroupedChat(
    val chat: Chat.Item,
    val showProfileAndName: Boolean,
    val showDate: Boolean,
    val showTime: Boolean,
    val showUnreadMarker: Boolean = false,
    val isSearchMatch: Boolean = false,
    val isCurrentSearchMatch: Boolean = false,
    val isFocused: Boolean = false
)

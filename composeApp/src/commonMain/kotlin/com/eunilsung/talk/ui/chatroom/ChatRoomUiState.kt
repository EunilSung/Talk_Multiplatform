package com.eunilsung.talk.ui.chatroom

import androidx.compose.runtime.Immutable
import com.eunilsung.talk.domain.model.GroupedChat

sealed interface ChatRoomUiState {
    data object Loading : ChatRoomUiState
    /** kotlin List 는 unstable 로 추론된다 — 내용이 불변임을 명시해 화면 단위 skip 을 살린다. */
    @Immutable
    data class Success(val groupedChats: List<GroupedChat>) : ChatRoomUiState
    data class Error(val message: String) : ChatRoomUiState
}

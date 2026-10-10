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

/** 안읽은 대화 요약의 진행 상태. 결과는 요청한 사람의 화면에만 뜬다. */
sealed interface UnreadSummaryUiState {
    data object Idle : UnreadSummaryUiState
    data object Loading : UnreadSummaryUiState
    data class Ready(val text: String) : UnreadSummaryUiState
    data object Empty : UnreadSummaryUiState
    data object Unavailable : UnreadSummaryUiState
}

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

/** 말풍선 아래에 붙는 번역의 상태. 번역은 요청한 사람의 화면에만 있고 화면을 나가면 사라진다. */
sealed interface ChatTranslationUiState {
    data object Loading : ChatTranslationUiState
    data class Ready(val text: String) : ChatTranslationUiState
}

/** 보내기 전의 글 다듬기 상태. */
sealed interface PolishUiState {
    data object Idle : PolishUiState
    data object Loading : PolishUiState

    /** [sourceText] 를 다듬은 결과. 그 사이 입력창이 바뀌었으면 화면이 덮어쓰지 않는다. */
    data class Ready(val sourceText: String, val text: String) : PolishUiState
    data object Failed : PolishUiState
}

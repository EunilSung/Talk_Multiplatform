package com.eunilsung.talk.ui.chatroom.linkpreview

import com.eunilsung.talk.domain.model.LinkPreviewState

/** 링크 미리보기 카드들의 상태. 아직 묻지 않은 주소는 들어 있지 않다. */
data class LinkPreviewUiState(
    val previews: Map<String, LinkPreviewState> = emptyMap(),
)

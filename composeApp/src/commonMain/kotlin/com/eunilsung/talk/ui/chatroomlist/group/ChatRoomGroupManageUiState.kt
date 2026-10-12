package com.eunilsung.talk.ui.chatroomlist.group

import com.eunilsung.talk.domain.model.ChatGroup

/** 대화 그룹 관리 화면의 상태 — 안읽음 그룹을 포함한 전체 그룹, 정렬 순. */
data class ChatRoomGroupManageUiState(
    val groups: List<ChatGroup> = emptyList(),
)

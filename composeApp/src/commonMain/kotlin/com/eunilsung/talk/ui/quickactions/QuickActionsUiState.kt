package com.eunilsung.talk.ui.quickactions

import com.eunilsung.talk.domain.model.Group

/** 빠른 액션의 상태 — 그룹 고르기 목록에 띄울 그룹. */
data class QuickActionsUiState(
    val groups: List<Group.Item> = emptyList(),
)

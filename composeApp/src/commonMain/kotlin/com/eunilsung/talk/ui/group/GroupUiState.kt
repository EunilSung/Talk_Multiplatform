package com.eunilsung.talk.ui.group

import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.ui.uikit.search.Search

/** 내 그룹 화면의 상태. */
data class GroupUiState(
    /** 검색어로 거른 그룹. 검색어가 없으면 전부다. */
    val items: List<Group.Item> = emptyList(),
    val isEditMode: Boolean = false,
    /** 입력 중인 검색어. [items] 는 잠시 뒤에 따라온다. */
    val search: Search.State = Search.State(),
    /** 편집 모드에서 고른 사람. */
    val selectedUserIds: Set<String> = emptySet(),
)

package com.eunilsung.talk.ui.invite.tab.group

import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.ui.uikit.search.Search

/** 초대 화면 그룹 탭의 상태. */
data class InviteGroupUiState(
    /** 검색어로 거른 그룹. 검색어가 없으면 전부다. */
    val items: List<Group.Item> = emptyList(),
    /** 입력 중인 검색어. [items] 는 잠시 뒤에 따라온다. */
    val search: Search.State = Search.State(),
)

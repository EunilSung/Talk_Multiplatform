package com.eunilsung.talk.ui.uikit.search

/** 목록 화면의 검색어와 고른 필터 칩. 화면 상태라 ui 에 둔다. */
sealed class Search{

    data class State(
        val query: String = "",
        val type: Type = Type.NAME,
        val filterType: FilterType = FilterType.ALL,
        /** [FilterType.GROUP] 선택 시 필터 대상 그룹 id. 그 외 필터에서는 null. */
        val selectedGroupId: String? = null
    )

    enum class Type {
        NAME, DEPARTMENT, CONTENT
    }

    enum class FilterType {
        ALL, UNREAD, RECEIVED, SENT, ALARM, GROUP
    }
}

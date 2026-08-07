package com.eunilsung.talk.domain.model

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

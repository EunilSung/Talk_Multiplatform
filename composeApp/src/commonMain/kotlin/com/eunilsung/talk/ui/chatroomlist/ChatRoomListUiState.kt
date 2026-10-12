package com.eunilsung.talk.ui.chatroomlist

import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.ui.uikit.search.Search

/** 대화함 화면의 상태. */
data class ChatRoomListUiState(
    /** 검색어·필터를 거쳐 화면에 보이는 방. */
    val chatItems: List<ChatRoom.Item> = emptyList(),
    /** [chatItems] 를 무엇으로 걸렀는지. 빈 목록일 때 어떤 안내를 띄울지 정한다. */
    val kind: ChatRoomListKind = ChatRoomListKind.IDLE,
    val isEditMode: Boolean = false,
    /** 입력 중인 검색어와 고른 필터 칩. 검색어는 바로 바뀌고 [chatItems] 는 잠시 뒤에 따라온다. */
    val search: Search.State = Search.State(filterType = Search.FilterType.ALL),
    /** 편집 모드에서 고른 방. */
    val selectedRoomIds: Set<String> = emptySet(),
    /** 안읽음 칩 배지에 쓰는 모든 방의 안읽음 합. */
    val unreadTotal: Int = 0,
    /** 대화그룹 칩. 안읽음·사용자 그룹 모두 정렬 순이다. */
    val chatGroups: List<ChatGroup> = emptyList(),
    /** 안읽은 방을 하나라도 가진 그룹 id. 그룹 칩 배지에 쓴다. */
    val groupsWithUnread: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
)

/** 목록을 거른 방식. */
enum class ChatRoomListKind {
    /** 전체 또는 그룹 칩. */
    IDLE,

    /** 검색어로 거른 결과. */
    SEARCH,

    /** 안읽음 칩. */
    UNREAD,
}

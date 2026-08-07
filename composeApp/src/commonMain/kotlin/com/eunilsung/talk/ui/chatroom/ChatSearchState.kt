package com.eunilsung.talk.ui.chatroom

import com.eunilsung.talk.domain.model.User

data class ChatSearchState(
    val isActive: Boolean = false,
    val query: String = "",
    val committedQuery: String = "",
    val searchedUser: User? = null,
    val searchedDate: String? = null,
    val matchedChatIds: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val focusChatId: String? = null,
    val focusTriggerKey: Int = 0
) {
    val currentMatchChatId: String? get() = matchedChatIds.getOrNull(currentIndex)

    val totalMatches: Int get() = matchedChatIds.size

    val currentPosition: Int get() = if (currentIndex < 0) 0 else currentIndex + 1
}

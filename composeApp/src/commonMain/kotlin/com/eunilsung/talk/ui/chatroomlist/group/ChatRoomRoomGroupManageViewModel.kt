package com.eunilsung.talk.ui.chatroomlist.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases

/** 대화 그룹 관리 화면 VM — 그룹 목록 구독 + 생성/이름변경/삭제/순서변경. */
class ChatRoomRoomGroupManageViewModel(
    private val useCases: ChatRoomListUseCases
) : ViewModel() {

    val groups: StateFlow<List<ChatGroup>> =
        useCases.observeChatGroups()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String) {
        viewModelScope.launch { runCatching { useCases.createChatGroup(name) } }
    }

    fun rename(groupId: String, newName: String) {
        viewModelScope.launch { runCatching { useCases.renameChatGroup(groupId, newName) } }
    }

    fun delete(groupId: String) {
        viewModelScope.launch { runCatching { useCases.deleteChatGroup(groupId) } }
    }

    fun reorder(orderedGroupIds: List<String>) {
        viewModelScope.launch { runCatching { useCases.reorderChatGroups(orderedGroupIds) } }
    }
}

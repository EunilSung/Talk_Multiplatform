package com.eunilsung.talk.ui.chatroomlist.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases

/** 대화 그룹 관리 화면 VM — 그룹 목록 구독 + 생성/이름변경/삭제/순서변경. */
class ChatRoomGroupManageViewModel(
    private val useCases: ChatRoomListUseCases
) : ViewModel() {

    val uiState: StateFlow<ChatRoomGroupManageUiState> =
        useCases.observeChatGroups()
            .map { ChatRoomGroupManageUiState(groups = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatRoomGroupManageUiState())

    fun onAction(action: ChatRoomGroupManageActions) {
        viewModelScope.launch {
            runCatching {
                when (action) {
                    is ChatRoomGroupManageActions.CreateGroup -> useCases.createChatGroup(action.name)
                    is ChatRoomGroupManageActions.RenameGroup ->
                        useCases.renameChatGroup(action.groupId, action.newName)
                    is ChatRoomGroupManageActions.DeleteGroup -> useCases.deleteChatGroup(action.groupId)
                    is ChatRoomGroupManageActions.ReorderGroups ->
                        useCases.reorderChatGroups(action.orderedGroupIds)
                }
            }
        }
    }
}

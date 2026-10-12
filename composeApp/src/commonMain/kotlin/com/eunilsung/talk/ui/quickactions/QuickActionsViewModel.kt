package com.eunilsung.talk.ui.quickactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.AddUserResult
import com.eunilsung.talk.domain.usecase.GroupUseCases
import com.eunilsung.talk.domain.usecase.InviteUseCases

/** 리스트 항목(사용자/그룹) 대상 빠른 액션 — 1:1 대화 시작, 그룹에 넣기, 그룹 고르기 목록. */
class QuickActionsViewModel(
    private val groupUseCases: GroupUseCases,
    private val inviteUseCases: InviteUseCases,
) : ViewModel() {

    val uiState: StateFlow<QuickActionsUiState> =
        groupUseCases.getGroups()
            .map { QuickActionsUiState(groups = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QuickActionsUiState())

    private val _events = Channel<QuickActionsEvent>(Channel.BUFFERED)
    val events: Flow<QuickActionsEvent> = _events.receiveAsFlow()

    fun onAction(action: QuickActionsActions) {
        when (action) {
            is QuickActionsActions.StartChat -> startChat(action.targetUserIds)
            is QuickActionsActions.AddUserToGroup -> addUserToGroup(action.userId, action.targetGroupId)
        }
    }

    private fun startChat(targetUserIds: List<String>) {
        val targetId = targetUserIds.filter { it.isNotBlank() }.distinct().singleOrNull() ?: return
        viewModelScope.launch {
            val roomId = runCatching { inviteUseCases.startDirectChat(targetId) }.getOrNull()
            _events.send(
                if (roomId.isNullOrBlank()) QuickActionsEvent.ChatCreateFailed
                else QuickActionsEvent.OpenChatRoom(roomId)
            )
        }
    }

    private fun addUserToGroup(userId: String, targetGroupId: String) {
        viewModelScope.launch {
            val event = when (groupUseCases.copyUserToGroup(userId, targetGroupId)) {
                AddUserResult.SUCCESS -> {
                    val groups = groupUseCases.getGroups().first()
                    QuickActionsEvent.AddedToGroup(groups.firstOrNull { it.id == targetGroupId })
                }
                AddUserResult.ALREADY_EXISTS -> QuickActionsEvent.AlreadyInGroup
                AddUserResult.FAILED -> QuickActionsEvent.AddToGroupFailed
            }
            _events.send(event)
        }
    }
}

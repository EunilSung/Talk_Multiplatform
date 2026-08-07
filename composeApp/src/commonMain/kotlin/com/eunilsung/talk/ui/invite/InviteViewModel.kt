package com.eunilsung.talk.ui.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.usecase.InviteUseCases
import com.eunilsung.talk.util.Log

class InviteViewModel(
    private val inviteUseCases: InviteUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteUiState())
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

    private val _submitResult = MutableSharedFlow<SubmitResult>(extraBufferCapacity = 4)
    val submitResult: SharedFlow<SubmitResult> = _submitResult.asSharedFlow()

    sealed class SubmitResult {
        data class Success(
            val chatRoomId: String,
            val selectedUserIds: List<String>,
            val selectedUsers: List<User> = emptyList(),
        ) : SubmitResult()
        data object Failure : SubmitResult()
    }

    fun onAction(action: InviteActions) {
        when (action) {
            is InviteActions.Init -> initWithMode(action.mode)
            is InviteActions.OnUserToggle -> toggleUser(action.user)
            is InviteActions.OnUserRemove -> removeUser(action.userId)
            is InviteActions.OnSubmit -> submit()
            is InviteActions.OnClose -> Unit
            is InviteActions.Reset -> _uiState.value = InviteUiState()
        }
    }

    private fun initWithMode(mode: InviteMode) {
        val existing: Set<String> = when (mode) {
            is InviteMode.InviteToChatRoom -> mode.existingUserIds.toSet()
            else -> emptySet()
        }
        _uiState.update { it.copy(mode = mode, existingUserIds = existing) }
        Log.message("[Invite] init mode=$mode existing=${existing.size}")
    }

    private fun toggleUser(user: User) {
        val key = user.id
        if (key.isBlank()) return
        _uiState.update { current ->
            val list = current.selectedUsers
            val next = if (list.any { it.id == key }) {
                list.filterNot { it.id == key }
            } else {
                list + user
            }
            current.copy(selectedUsers = next)
        }
    }

    private fun removeUser(userId: String) {
        if (userId.isBlank()) return
        _uiState.update { current ->
            current.copy(selectedUsers = current.selectedUsers.filterNot { it.id == userId })
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.selectedUsers.isEmpty()) return

        val pairs: List<Pair<String, String>> = state.selectedUsers.mapNotNull { user ->
            val id = user.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to user.name
        }
        if (pairs.isEmpty()) return
        val userIds = pairs.map { it.first }

        when (val mode = state.mode) {
            is InviteMode.InviteToChatRoom ->
                if (mode.createNewRoom) {
                    val myId = Config.MyInfo.userId
                    val combined = (mode.existingUsers.filter { it.first != myId } + pairs)
                        .distinctBy { it.first }
                    submitInvite(chatRoomId = "", invitedUsers = combined, existingUserCount = 0)
                } else {
                    submitInvite(mode.chatRoomId, pairs, mode.existingUserIds.size)
                }
            is InviteMode.CreateChatRoom ->
                submitInvite(chatRoomId = "", invitedUsers = pairs, existingUserCount = 0)
        }
    }

    private fun submitInvite(
        chatRoomId: String,
        invitedUsers: List<Pair<String, String>>,
        existingUserCount: Int,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val resultId = runCatching {
                inviteUseCases.inviteUsers(chatRoomId, invitedUsers, existingUserCount)
            }.getOrNull()
            _uiState.update { it.copy(isSubmitting = false) }
            val userIds = invitedUsers.map { it.first }
            _submitResult.tryEmit(
                if (!resultId.isNullOrBlank())
                    SubmitResult.Success(chatRoomId = resultId, selectedUserIds = userIds)
                else SubmitResult.Failure
            )
        }
    }
}

package com.eunilsung.talk.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.data.remote.share.SharedContent
import com.eunilsung.talk.data.remote.share.cleanupSharedFile
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases
import com.eunilsung.talk.domain.usecase.ChatRoomUseCases
import com.eunilsung.talk.domain.usecase.InviteUseCases
import com.eunilsung.talk.util.Log

class ShareViewModel(
    chatRoomListUseCases: ChatRoomListUseCases,
    private val inviteUseCases: InviteUseCases,
    private val chatRoomUseCases: ChatRoomUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    val chatRooms: StateFlow<List<ChatRoom.Item>> = run {
        val flow = MutableStateFlow<List<ChatRoom.Item>>(emptyList())
        viewModelScope.launch {
            chatRoomListUseCases.getChatRooms().collect { rooms ->
                flow.value = rooms.filter { it.enableMode != "1" }
            }
        }
        flow.asStateFlow()
    }

    private val _submitResult = MutableSharedFlow<SubmitResult>(extraBufferCapacity = 4)
    val submitResult: SharedFlow<SubmitResult> = _submitResult.asSharedFlow()

    sealed class SubmitResult {
        data class NavigateToRoom(val chatRoomId: String) : SubmitResult()
        data object Failure : SubmitResult()
    }

    fun onAction(action: ShareActions) {
        when (action) {
            is ShareActions.Init -> initWithContent(action.content)
            is ShareActions.OnUserToggle -> toggleUser(action.user)
            is ShareActions.OnUserRemove -> removeUser(action.userId)
            is ShareActions.OnChatRoomSelect -> selectChatRoom(action.chatRoom)
            is ShareActions.OnChatRoomClear ->
                _uiState.update { it.copy(selectedChatRoom = null) }
            is ShareActions.OnSubmit -> submit()
            is ShareActions.OnClose -> Unit
            is ShareActions.Reset -> _uiState.value = ShareUiState()
        }
    }

    private fun initWithContent(content: SharedContent) {
        _uiState.update { it.copy(sharedContent = content) }
        Log.message("[Share] init content text=${content.text.length} files=${content.filePaths.size}")
    }

    private fun toggleUser(user: User) {
        val key = user.id
        if (key.isBlank()) return
        _uiState.update { current ->
            val list = current.selectedUsers
            val alreadySelected = list.any { it.id == key }
            val nextList = if (alreadySelected) {
                list.filterNot { it.id == key }
            } else {
                list + user
            }
            current.copy(
                selectedUsers = nextList,
                selectedChatRoom = if (!alreadySelected) null else current.selectedChatRoom
            )
        }
    }

    private fun removeUser(userId: String) {
        if (userId.isBlank()) return
        _uiState.update { current ->
            current.copy(selectedUsers = current.selectedUsers.filterNot { it.id == userId })
        }
    }

    private fun selectChatRoom(room: ChatRoom.Item) {
        _uiState.update { current ->
            val same = current.selectedChatRoom?.id == room.id
            current.copy(
                selectedChatRoom = if (same) null else room,
                selectedUsers = if (!same) emptyList() else current.selectedUsers
            )
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }

            val roomId: String? = when {
                state.selectedUsers.isNotEmpty() -> {
                    val pairs = state.selectedUsers.mapNotNull { user ->
                        val id = user.id.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        id to user.name
                    }
                    if (pairs.isEmpty()) null
                    else runCatching {
                        inviteUseCases.inviteUsers(
                            chatRoomId = "",
                            invitedUsers = pairs,
                            existingUserCount = 0,
                        )
                    }.onFailure { Log.message("[Share] inviteUsers failed: ${it.message}") }
                        .getOrNull()
                }
                state.selectedChatRoom != null ->
                    state.selectedChatRoom.id.takeIf { it.isNotBlank() }
                else -> null
            }

            if (roomId.isNullOrBlank()) {
                _uiState.update { it.copy(isSubmitting = false) }
                _submitResult.tryEmit(SubmitResult.Failure)
                return@launch
            }

            Log.message("[Share] navigate to room=$roomId text=${state.sharedContent.text.length} files=${state.sharedContent.filePaths.size}")
            _submitResult.tryEmit(SubmitResult.NavigateToRoom(roomId))

            delay(300)

            val content = state.sharedContent
            if (content.hasText && !content.hasFiles) {
                runCatching { chatRoomUseCases.sendTextChat(roomId, content.text) }
                    .onFailure { Log.message("[Share] sendTextChat failed: ${it.message}") }
            }
            content.filePaths.forEach { path ->
                runCatching { chatRoomUseCases.sendFile(roomId, path) }
                    .onFailure { Log.message("[Share] sendFile failed path=$path: ${it.message}") }
                runCatching { cleanupSharedFile(path) }
            }

            _uiState.update { it.copy(isSubmitting = false) }
        }
    }
}

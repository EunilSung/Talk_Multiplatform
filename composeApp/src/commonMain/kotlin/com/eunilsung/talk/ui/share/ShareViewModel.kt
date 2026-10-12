package com.eunilsung.talk.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.model.SharedContent
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases
import com.eunilsung.talk.domain.usecase.ChatRoomUseCases
import com.eunilsung.talk.domain.usecase.InviteUseCases
import com.eunilsung.talk.util.Log
import com.eunilsung.talk.domain.usecase.CleanupSharedFileUseCase

class ShareViewModel(
    chatRoomListUseCases: ChatRoomListUseCases,
    private val inviteUseCases: InviteUseCases,
    private val chatRoomUseCases: ChatRoomUseCases,
    private val cleanupSharedFile: CleanupSharedFileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private val _events = Channel<ShareEvent>(Channel.BUFFERED)
    val events: Flow<ShareEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            chatRoomListUseCases.getChatRooms().collect { rooms ->
                _uiState.update { it.copy(rooms = rooms.filter { room -> room.enableMode != "1" }) }
            }
        }
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
            is ShareActions.Reset -> _uiState.update { ShareUiState(rooms = it.rooms) }
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
                _events.send(ShareEvent.SendFailed)
                return@launch
            }

            Log.message("[Share] navigate to room=$roomId text=${state.sharedContent.text.length} files=${state.sharedContent.filePaths.size}")
            _events.send(ShareEvent.OpenChatRoom(roomId))

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

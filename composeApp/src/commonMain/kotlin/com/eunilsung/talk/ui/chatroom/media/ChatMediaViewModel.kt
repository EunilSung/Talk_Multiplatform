package com.eunilsung.talk.ui.chatroom.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eunilsung.talk.domain.model.OpenFileResult
import com.eunilsung.talk.domain.usecase.ChatMediaUseCases
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 대화방 모아보기 — 방의 사진·동영상·파일을 탭으로 나눠 보인다.
 *
 * 서버에 묻지 않고 이 기기의 DB 에 받아 둔 대화만 보인다. 파일 받기·열기는 대화방과 같은 유스케이스로 한다.
 */
class ChatMediaViewModel(
    private val useCases: ChatMediaUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatMediaUiState())
    val uiState: StateFlow<ChatMediaUiState> = _uiState.asStateFlow()

    private val _events = Channel<ChatMediaEvent>(Channel.BUFFERED)
    val events: Flow<ChatMediaEvent> = _events.receiveAsFlow()

    fun onAction(action: ChatMediaActions) {
        when (action) {
            is ChatMediaActions.Load -> load(action.chatRoomId)
            is ChatMediaActions.OnTabChange -> _uiState.update { it.copy(tab = action.tab) }
            is ChatMediaActions.OnDownloadFile -> downloadFile(action.source, action.displayName)
            is ChatMediaActions.OnOpenFile -> openFile(action.source, action.displayName)
        }
    }

    private fun load(chatRoomId: String) {
        _uiState.value = ChatMediaUiState(chatRoomId = chatRoomId)
        viewModelScope.launch {
            val media = useCases.getMediaChats(chatRoomId)
            _uiState.update { if (it.chatRoomId == chatRoomId) it.withMedia(media) else it }
        }
    }

    private fun downloadFile(source: String, displayName: String) {
        viewModelScope.launch {
            val saved = useCases.downloadFile(source, displayName)
            _events.send(if (saved) ChatMediaEvent.FileDownloaded(displayName) else ChatMediaEvent.FileDownloadFailed)
        }
    }

    private fun openFile(source: String, displayName: String) {
        viewModelScope.launch {
            when (useCases.openFile(source, displayName)) {
                OpenFileResult.OPENED -> Unit
                OpenFileResult.DOWNLOADED_AND_OPENED -> _events.send(ChatMediaEvent.FileDownloaded(displayName))
                OpenFileResult.FAILED -> _events.send(ChatMediaEvent.FileOpenFailed)
            }
        }
    }
}

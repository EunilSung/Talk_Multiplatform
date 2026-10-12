package com.eunilsung.talk.ui.chatroom.linkpreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.usecase.GetLinkPreviewUseCase

/** 대화방의 링크 미리보기 카드들이 함께 쓰는 VM. 같은 주소는 한 번만 받아 온다. */
class LinkPreviewViewModel(
    private val getLinkPreview: GetLinkPreviewUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkPreviewUiState())
    val uiState: StateFlow<LinkPreviewUiState> = _uiState.asStateFlow()

    private val requestedUrls = mutableSetOf<String>()

    fun onAction(action: LinkPreviewActions) {
        when (action) {
            is LinkPreviewActions.Load -> load(action.url)
        }
    }

    private fun load(url: String) {
        if (!requestedUrls.add(url)) return
        viewModelScope.launch {
            getLinkPreview(url).collect { state ->
                _uiState.update { it.copy(previews = it.previews + (url to state)) }
            }
        }
    }
}

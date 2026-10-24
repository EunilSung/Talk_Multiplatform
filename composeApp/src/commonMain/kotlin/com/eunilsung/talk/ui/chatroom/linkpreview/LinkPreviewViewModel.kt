package com.eunilsung.talk.ui.chatroom.linkpreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.LinkPreviewState
import com.eunilsung.talk.domain.usecase.GetLinkPreviewUseCase
import com.eunilsung.talk.domain.util.isPreviewableUrl

/**
 * 대화방의 링크 미리보기 카드들이 함께 쓰는 VM. 같은 주소는 한 번만 받아 온다.
 *
 * 받은 대화의 링크는 저절로 받지 않는다 — 보낸 사람이 받는 사람의 IP·열람 시각을 알아내거나 내부망 주소로 요청을 보내게
 * 할 수 있어서다. 사용자가 누를 때 받는다. 받으면 안 되는 주소([isPreviewableUrl])는 묻지 않고 카드를 숨긴다.
 */
class LinkPreviewViewModel(
    private val getLinkPreview: GetLinkPreviewUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkPreviewUiState())
    val uiState: StateFlow<LinkPreviewUiState> = _uiState.asStateFlow()

    private val requestedUrls = mutableSetOf<String>()

    fun onAction(action: LinkPreviewActions) {
        when (action) {
            is LinkPreviewActions.OnShown -> onShown(action.url, action.isMe)
            is LinkPreviewActions.Load -> load(action.url)
        }
    }

    private fun onShown(url: String, isMe: Boolean) {
        when {
            url in requestedUrls -> Unit
            !isPreviewableUrl(url) -> {
                requestedUrls += url
                _uiState.update { it.copy(previews = it.previews + (url to LinkPreviewState.Failure)) }
            }
            isMe -> load(url)
            else -> _uiState.update { it.copy(waitingUrls = it.waitingUrls + url) }
        }
    }

    private fun load(url: String) {
        if (!isPreviewableUrl(url)) return
        _uiState.update { it.copy(waitingUrls = it.waitingUrls - url) }
        if (!requestedUrls.add(url)) return
        viewModelScope.launch {
            getLinkPreview(url).collect { state ->
                _uiState.update { it.copy(previews = it.previews + (url to state)) }
            }
        }
    }
}

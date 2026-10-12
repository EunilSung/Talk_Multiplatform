package com.eunilsung.talk.ui.uikit.mediapicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.usecase.SaveToGalleryUseCase

/** 사진 상세보기 — 받은 사진·동영상을 사진첩에 저장한다. */
class PhotoDetailViewModel(
    private val saveToGallery: SaveToGalleryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoDetailUiState())
    val uiState: StateFlow<PhotoDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<PhotoDetailEvent>(Channel.BUFFERED)
    val events: Flow<PhotoDetailEvent> = _events.receiveAsFlow()

    fun onAction(action: PhotoDetailActions) {
        when (action) {
            is PhotoDetailActions.OnSaveToGallery -> save(action.source, action.saveAs, action.isVideo)
        }
    }

    private fun save(source: String, saveAs: String, isVideo: Boolean) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val saved = saveToGallery(source, saveAs, isVideo)
            _uiState.update { it.copy(isSaving = false) }
            _events.send(if (saved) PhotoDetailEvent.Saved(saveAs) else PhotoDetailEvent.SaveFailed(isVideo))
        }
    }
}

package com.eunilsung.talk.ui.userprofile

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
import com.eunilsung.talk.data.local.RecentPhotosProvider
import com.eunilsung.talk.data.local.RecentPhotosResult
import com.eunilsung.talk.domain.usecase.SaveMyProfilePhotoUseCase

/** 프로필 사진 고르기 화면 VM — 최근 사진 읽기, 사진 권한, 고른 사진을 내 프로필 사진으로 저장. */
class ProfilePhotoPickerViewModel(
    private val saveMyProfilePhoto: SaveMyProfilePhotoUseCase,
    private val recentPhotosProvider: RecentPhotosProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilePhotoPickerUiState())
    val uiState: StateFlow<ProfilePhotoPickerUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfilePhotoPickerEvent>(Channel.BUFFERED)
    val events: Flow<ProfilePhotoPickerEvent> = _events.receiveAsFlow()

    fun onAction(action: ProfilePhotoPickerActions) {
        when (action) {
            is ProfilePhotoPickerActions.OnStart -> {
                if (recentPhotosProvider.hasPhotoAccess()) loadRecent()
                else _uiState.update { it.copy(showPermissionDialog = true) }
            }
            is ProfilePhotoPickerActions.OnResume -> {
                if (recentPhotosProvider.hasPhotoAccess() && _uiState.value.photos.isEmpty()) loadRecent()
            }
            is ProfilePhotoPickerActions.OnRequestPhotoAccess -> {
                _uiState.update { it.copy(showPermissionDialog = false) }
                recentPhotosProvider.requestPhotoAccess()
            }
            is ProfilePhotoPickerActions.OnDismissPermission ->
                _uiState.update { it.copy(showPermissionDialog = false) }
            is ProfilePhotoPickerActions.OnUpload -> upload(action.uri)
        }
    }

    private fun loadRecent() {
        viewModelScope.launch {
            val result = runCatching { recentPhotosProvider.fetchRecent(RECENT_LIMIT, onPartial = ::applyPhotos) }
                .getOrElse { RecentPhotosResult.Granted(emptyList()) }
            applyPhotos(result)
        }
    }

    private fun applyPhotos(result: RecentPhotosResult) {
        val photos = when (result) {
            is RecentPhotosResult.Granted -> result.photos.filter { !it.isVideo }
            is RecentPhotosResult.Limited -> result.photos.filter { !it.isVideo }
            is RecentPhotosResult.Denied -> emptyList()
        }
        _uiState.update { it.copy(photos = photos) }
    }

    private fun upload(uri: String) {
        if (uri.isBlank() || _uiState.value.isUploading) return
        _uiState.update { it.copy(isUploading = true) }
        viewModelScope.launch {
            try {
                val saved = saveMyProfilePhoto(uri)
                if (saved != null) _events.send(ProfilePhotoPickerEvent.Saved(saved))
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    private companion object {
        const val RECENT_LIMIT = 500
    }
}

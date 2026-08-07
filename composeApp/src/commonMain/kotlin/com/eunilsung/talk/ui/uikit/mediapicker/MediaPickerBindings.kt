package com.eunilsung.talk.ui.uikit.mediapicker

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.data.local.PhotoAlbum
import com.eunilsung.talk.data.local.RecentPhoto
import com.eunilsung.talk.data.local.RecentPhotosResult

@Immutable
data class MediaPickerBindings(
    val recentPhotosFlow: StateFlow<RecentPhotosResult>,
    val galleryPhotos: List<RecentPhoto>,
    /** 갤러리 시트 상단 필터에 뿌릴 앨범(폴더) 목록. 비어 있으면 필터를 숨긴다. */
    val galleryAlbums: List<PhotoAlbum> = emptyList(),
    /** 선택된 앨범 id. null = 전체. */
    val selectedAlbumId: String? = null,
    /** 앨범 선택 — 해당 폴더의 사진만 다시 읽는다. */
    val onSelectGalleryAlbum: (String?) -> Unit = {},
    val hasPhotoAccess: () -> Boolean,
    val onLoadRecentPhotos: () -> Unit,
    val onLoadGalleryPhotos: () -> Unit,
    val onRequestPhotoAccess: () -> Unit,
    val onLaunchFilePicker: () -> Unit,
    val onSendPhotos: (List<String>) -> Unit,
    val onCameraCapture: (path: String) -> Unit = {},
) {
    companion object {
        val Preview: MediaPickerBindings = MediaPickerBindings(
            recentPhotosFlow = MutableStateFlow(RecentPhotosResult.Granted(emptyList())),
            galleryPhotos = emptyList(),
            hasPhotoAccess = { false },
            onLoadRecentPhotos = {},
            onLoadGalleryPhotos = {},
            onRequestPhotoAccess = {},
            onLaunchFilePicker = {},
            onSendPhotos = {},
            onCameraCapture = {},
        )
    }
}

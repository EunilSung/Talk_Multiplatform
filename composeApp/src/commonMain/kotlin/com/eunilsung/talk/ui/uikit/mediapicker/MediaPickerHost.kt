package com.eunilsung.talk.ui.uikit.mediapicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.ismoy.imagepickerkmp.domain.extensions.absolutePath
import io.github.ismoy.imagepickerkmp.features.imagepicker.model.ImagePickerResult
import io.github.ismoy.imagepickerkmp.features.imagepicker.ui.rememberImagePickerKMP
import com.eunilsung.talk.data.local.RecentPhoto
import com.eunilsung.talk.data.local.RecentPhotosResult
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import com.eunilsung.talk.ui.uikit.sheet.BottomSheet
import com.eunilsung.talk.util.Log
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.photo_permission_title
import multiplatformtalk.composeapp.generated.resources.photo_permission_message
import multiplatformtalk.composeapp.generated.resources.ok
import multiplatformtalk.composeapp.generated.resources.cancel
import org.jetbrains.compose.resources.stringResource

@Stable
class MediaPickerState {
    internal var showMultimediaSheet by mutableStateOf(false)
    internal var showGallerySheet by mutableStateOf(false)
    internal var transitioningToGallery by mutableStateOf(false)
    internal var selectedPhotoIds by mutableStateOf<List<String>>(emptyList())
    internal var detailPhoto by mutableStateOf<MultimediaRecentPhoto?>(null)

    fun open() {
        showMultimediaSheet = true
    }

    fun showDetail(photo: MultimediaRecentPhoto) {
        detailPhoto = photo
    }

    fun closeAll() {
        showMultimediaSheet = false
        showGallerySheet = false
        transitioningToGallery = false
        selectedPhotoIds = emptyList()
        detailPhoto = null
    }
}

@Composable
fun rememberMediaPickerState(): MediaPickerState = remember { MediaPickerState() }

@Composable
fun MediaPickerHost(
    state: MediaPickerState,
    bindings: MediaPickerBindings,
) {
    LaunchedEffect(state.showMultimediaSheet) {
        if (state.showMultimediaSheet) bindings.onLoadRecentPhotos()
    }
    LaunchedEffect(state.showGallerySheet) {
        if (state.showGallerySheet) bindings.onLoadGalleryPhotos()
    }

    LaunchedEffect(state.showMultimediaSheet) {
        if (!state.showMultimediaSheet && !state.transitioningToGallery) {
            state.selectedPhotoIds = emptyList()
        }
    }
    LaunchedEffect(state.showGallerySheet) {
        if (!state.showGallerySheet) {
            state.selectedPhotoIds = emptyList()
            state.transitioningToGallery = false
        }
    }

    val picker = rememberImagePickerKMP()
    LaunchedEffect(picker.result) {
        when (val r = picker.result) {
            is ImagePickerResult.Success -> {
                val paths = r.photos.mapNotNull { it.absolutePath }
                Log.message("[MediaPicker] camera captured paths=$paths")
                paths.forEach { bindings.onCameraCapture(it) }
                picker.reset()
            }
            is ImagePickerResult.Error -> {
                Log.message("[MediaPicker] camera error: ${r.exception.message}")
                picker.reset()
            }
            is ImagePickerResult.Dismissed -> picker.reset()
            else -> Unit
        }
    }

    BottomSheet(
        isVisible = state.showMultimediaSheet,
        onDismiss = { state.showMultimediaSheet = false },
    ) {
        val dialog = LocalDialogManager.current
        val txtPermTitle = stringResource(Res.string.photo_permission_title)
        val txtPermMsg = stringResource(Res.string.photo_permission_message)
        val txtOk = stringResource(Res.string.ok)
        val txtCancel = stringResource(Res.string.cancel)
        val recentPhotosResult by bindings.recentPhotosFlow.collectAsState()
        val photos = when (val r = recentPhotosResult) {
            is RecentPhotosResult.Granted -> r.photos
            is RecentPhotosResult.Limited -> r.photos
            is RecentPhotosResult.Denied -> emptyList()
        }

        // 같은 람다가 selectedPhotoIds 를 읽으므로, remember 없이 매핑하면 사진을 한 장 고를
        // 때마다 목록 전체가 다시 변환된다. 시트가 보여주는 만큼만 잘라 쓴다.
        val recentPhotos = remember(photos) {
            photos.take(MAX_RECENT_PHOTOS).map { it.toMultimediaPhoto() }
        }

        MultimediaBottomSheetContent(
            recentPhotos = recentPhotos,
            selectedIds = state.selectedPhotoIds,
            onCamera = {
                state.showMultimediaSheet = false
                picker.launchCamera()
            },
            onPhotoToggle = { p ->
                val cur = state.selectedPhotoIds
                state.selectedPhotoIds = if (p.id in cur) cur - p.id else cur + p.id
            },
            onPhotoDetailClick = { state.detailPhoto = it },
            onSend = {
                bindings.onSendPhotos(state.selectedPhotoIds)
                state.showMultimediaSheet = false
            },
            onViewAllPhotos = {
                if (!bindings.hasPhotoAccess()) {
                    dialog.confirm(
                        title = txtPermTitle,
                        message = txtPermMsg,
                        confirmText = txtOk,
                        dismissText = txtCancel,
                        onConfirm = {
                            state.showMultimediaSheet = false
                            bindings.onRequestPhotoAccess()
                        },
                    )
                } else {
                    state.transitioningToGallery = true
                    state.showMultimediaSheet = false
                    state.showGallerySheet = true
                }
            },
            onFile = {
                state.showMultimediaSheet = false
                bindings.onLaunchFilePicker()
            },
        )
    }

    BottomSheet(
        isVisible = state.showGallerySheet,
        onDismiss = { state.showGallerySheet = false },
        isModal = false,
    ) {
        // 기기 갤러리 전량이라 상한이 없다. remember 없이 두면 사진 탭 한 번에 전량 재변환.
        val galleryPhotos = remember(bindings.galleryPhotos) {
            bindings.galleryPhotos.map { it.toMultimediaPhoto() }
        }

        PhotoGalleryBottomSheetContent(
            photos = galleryPhotos,
            selectedIds = state.selectedPhotoIds,
            onClose = { state.showGallerySheet = false },
            onSend = {
                bindings.onSendPhotos(state.selectedPhotoIds)
                state.showGallerySheet = false
            },
            onCameraClick = {
                state.showGallerySheet = false
                picker.launchCamera()
            },
            onPhotoToggle = { p ->
                val cur = state.selectedPhotoIds
                state.selectedPhotoIds = if (p.id in cur) cur - p.id else cur + p.id
            },
            onPhotoDetailClick = { state.detailPhoto = it },
            albums = bindings.galleryAlbums,
            selectedAlbumId = bindings.selectedAlbumId,
            onAlbumSelect = { albumId ->
                // 폴더가 바뀌면 이전 폴더에서 고른 항목은 화면에서 사라지므로 선택도 비운다.
                state.selectedPhotoIds = emptyList()
                bindings.onSelectGalleryAlbum(albumId)
            },
        )
    }

    state.detailPhoto?.let { photo ->
        PhotoDetailDialog(
            photo = photo,
            onDismiss = { state.detailPhoto = null },
        )
    }
}

/** data 레이어의 [RecentPhoto] → 시트가 쓰는 표시용 모델. */
private fun RecentPhoto.toMultimediaPhoto() = MultimediaRecentPhoto(
    id = id,
    uri = uri,
    thumbnailBytes = thumbnailBytes,
    isVideo = isVideo,
    durationSec = durationSec,
)

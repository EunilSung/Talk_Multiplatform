package com.eunilsung.talk.ui.chatroom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.eunilsung.talk.data.local.FilePickerProvider
import com.eunilsung.talk.data.local.FilePickerRequestId
import com.eunilsung.talk.data.local.RecentPhotosProvider
import com.eunilsung.talk.data.local.RecentPhotosResult
import com.eunilsung.talk.util.Log

/**
 * 대화방 VM 의 사진·파일 보내기 부분 — 최근 사진·앨범 읽기, 사진 권한, 파일 고르기 창.
 *
 * 읽은 결과는 [ChatRoomUiState.media] 에 쓴다. 고른 파일을 보내는 일은 VM 이 한다.
 */
internal class ChatMediaHandler(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ChatRoomUiState>,
    private val recentPhotosProvider: RecentPhotosProvider,
    private val filePickerProvider: FilePickerProvider,
) {
    /** picker 결과가 싱글톤 provider 로 broadcast 되므로, 이 VM 이 띄운 요청만 소비하기 위한 태그. */
    private val filePickerRequestId =
        FilePickerRequestId.next("chatroom")

    private var galleryJob: Job? = null

    /** 이 화면이 띄운 파일 고르기 창에서 파일을 고르면 [onPicked] 로 경로를 넘긴다. */
    fun collectPickedFiles(onPicked: (List<String>) -> Unit) {
        scope.launch {
            filePickerProvider.results.collect { files ->
                if (files.isEmpty()) return@collect
                if (filePickerProvider.lastRequestId != filePickerRequestId) return@collect
                Log.message(
                    "[FilePicker] received ${files.size} files: " +
                            files.joinToString { "${it.name}(${it.sizeBytes})" }
                )
                onPicked(files.map { it.uri })
            }
        }
    }

    fun launchFilePicker() {
        filePickerProvider.launchFilePicker(
            requestId = filePickerRequestId,
            allowMultiple = true,
            mimeTypes = listOf("*/*"),
        )
    }

    fun requestPhotoAccess() {
        recentPhotosProvider.requestPhotoAccess()
    }

    private fun updateMedia(transform: (ChatMediaUiState) -> ChatMediaUiState) {
        uiState.update { it.copy(media = transform(it.media)) }
    }

    /** 앨범 선택 — 해당 폴더의 사진만 다시 읽는다. */
    fun selectGalleryAlbum(albumId: String?) {
        updateMedia { it.copy(selectedAlbumId = albumId, galleryPhotos = emptyList()) }
        loadGalleryPhotos()
    }

    /**
     * 갤러리 사진 로딩.
     *
     * 이전 로딩은 반드시 취소하고, 결과를 쓰기 전에 요청 당시 앨범이 아직 선택 상태인지 확인한다.
     * iOS 는 썸네일을 디코드하며 onPartial 로 중간 결과를 계속 흘리므로, 앨범을 빠르게 오가면
     * 이전 앨범의 늦은 콜백이 새 목록을 덮어써 엉뚱한 사진이 보인다.
     * (취소는 즉시 전파되지 않아 이미 출발한 콜백이 남을 수 있어 확인이 따로 필요하다.)
     */
    fun loadGalleryPhotos(limit: Int = 500) {
        val albumId = uiState.value.media.selectedAlbumId
        updateMedia { it.copy(hasPhotoAccess = recentPhotosProvider.hasPhotoAccess()) }
        galleryJob?.cancel()
        galleryJob = scope.launch {
            fun apply(r: RecentPhotosResult) {
                if (uiState.value.media.selectedAlbumId != albumId) return
                val photos = when (r) {
                    is RecentPhotosResult.Granted -> r.photos
                    is RecentPhotosResult.Limited -> r.photos
                    is RecentPhotosResult.Denied -> emptyList()
                }
                updateMedia { it.copy(galleryPhotos = photos) }
            }
            val result = runCatching {
                recentPhotosProvider.fetchByAlbum(
                    albumId = albumId,
                    limit = limit,
                    onPartial = ::apply,
                )
            }.getOrElse { RecentPhotosResult.Granted(emptyList()) }
            if (isActive) apply(result)
        }
        loadGalleryAlbums()
    }

    /**
     * 앨범 목록 조회 — 시트를 열 때 한 번만.
     *
     * 사진 로딩과 별도 코루틴으로 돌린다. iOS 는 사진 500장의 썸네일을 전부 디코드한 뒤에야
     * 조회가 끝나므로, 뒤에 붙이면 필터 버튼이 한참 뒤에야 나타난다.
     */
    private fun loadGalleryAlbums() {
        if (uiState.value.media.galleryAlbums.isNotEmpty()) return
        scope.launch {
            val albums = runCatching { recentPhotosProvider.fetchAlbums() }
                .getOrElse { emptyList() }
            updateMedia { it.copy(galleryAlbums = albums) }
        }
    }

    fun loadRecentPhotos(limit: Int = 12) {
        updateMedia { it.copy(hasPhotoAccess = recentPhotosProvider.hasPhotoAccess()) }
        scope.launch {
            val result = runCatching { recentPhotosProvider.fetchRecent(limit) }
                .getOrElse { RecentPhotosResult.Granted(emptyList()) }
            updateMedia { it.copy(recentPhotos = result) }
        }
    }
}

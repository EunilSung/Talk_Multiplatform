package com.eunilsung.talk.ui.uikit.mediapicker

sealed interface PhotoDetailActions {
    /** 사진첩에 저장한다. [source] 는 읽을 경로, [saveAs] 는 사진첩에 남길 이름이다. */
    data class OnSaveToGallery(val source: String, val saveAs: String, val isVideo: Boolean) : PhotoDetailActions
}

sealed interface PhotoDetailEvent {
    data class Saved(val saveAs: String) : PhotoDetailEvent

    data class SaveFailed(val isVideo: Boolean) : PhotoDetailEvent
}

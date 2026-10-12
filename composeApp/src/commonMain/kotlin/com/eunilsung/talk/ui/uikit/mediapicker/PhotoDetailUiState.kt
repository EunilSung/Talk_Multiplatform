package com.eunilsung.talk.ui.uikit.mediapicker

/** 사진 상세보기의 상태. 저장하는 동안에는 저장 버튼을 막는다. */
data class PhotoDetailUiState(
    val isSaving: Boolean = false,
)

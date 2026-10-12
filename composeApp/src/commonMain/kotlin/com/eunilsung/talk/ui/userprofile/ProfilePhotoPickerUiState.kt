package com.eunilsung.talk.ui.userprofile

import com.eunilsung.talk.data.local.RecentPhoto

/** 프로필 사진 고르기 화면의 상태. 동영상은 빼고 사진만 담는다. */
data class ProfilePhotoPickerUiState(
    val photos: List<RecentPhoto> = emptyList(),
    /** 고른 사진을 저장하는 중. 화면 전체를 막고 진행 표시를 띄운다. */
    val isUploading: Boolean = false,
    val showPermissionDialog: Boolean = false,
)

package com.eunilsung.talk.ui.userprofile

sealed interface ProfilePhotoPickerActions {
    /** 화면이 떴다. 사진첩을 볼 수 있으면 최근 사진을 읽고, 없으면 권한 안내를 띄운다. */
    data object OnStart : ProfilePhotoPickerActions

    /** 설정 앱 등에서 돌아왔다. 그 사이 권한을 받았으면 사진을 읽는다. */
    data object OnResume : ProfilePhotoPickerActions

    /** 권한 안내에서 확인을 눌렀다. 시스템 권한 창을 띄운다. */
    data object OnRequestPhotoAccess : ProfilePhotoPickerActions

    data object OnDismissPermission : ProfilePhotoPickerActions

    /** 이 사진을 내 프로필 사진으로 쓴다. */
    data class OnUpload(val uri: String) : ProfilePhotoPickerActions
}

sealed interface ProfilePhotoPickerEvent {
    /** 프로필 사진을 바꿨다. [path] 는 저장한 사진의 경로. */
    data class Saved(val path: String) : ProfilePhotoPickerEvent
}

package com.eunilsung.talk.data.local

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.flow.MutableSharedFlow

/** MainActivity 가 등록하는 Android picker / launcher 접근 홀더. */
object AndroidActivityHolder {
    @Volatile var activity: ComponentActivity? = null
    @Volatile var filePickerLauncher: ActivityResultLauncher<Array<String>>? = null

    /** PickMultipleVisualMedia launcher — Photo Picker API. */
    @Volatile var photoPickerLauncher:
            ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest>? = null

    /** 사진/동영상 권한 요청 launcher. */
    @Volatile var photoPermissionLauncher: ActivityResultLauncher<Array<String>>? = null

    /** 갤러리 앱 진입 launcher — `GetMultipleContents` 컨트랙트. */
    @Volatile var galleryContentLauncher: ActivityResultLauncher<String>? = null

    /** OpenMultipleDocuments 결과 broadcast. */
    val pickedFiles = MutableSharedFlow<List<PickedFile>>(
        replay = 0,
        extraBufferCapacity = 8,
    )

    /** PickMultipleVisualMedia 결과 broadcast. */
    val pickedPhotos = MutableSharedFlow<List<PickedPhoto>>(
        replay = 0,
        extraBufferCapacity = 8,
    )
}

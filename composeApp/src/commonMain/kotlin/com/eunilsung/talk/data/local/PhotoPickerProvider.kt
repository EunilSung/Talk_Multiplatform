package com.eunilsung.talk.data.local

import kotlinx.coroutines.flow.SharedFlow

/** 갤러리에서 사진만 선택하는 picker — 권한 불필요한 modern API 사용. */
interface PhotoPickerProvider {
    /** picker 종료 후 선택한 사진(들)의 결과 stream. 취소 시 빈 리스트. */
    val results: SharedFlow<List<PickedPhoto>>

    /**
     * Picker 실행.
     * @param allowMultiple true 면 다중 선택 허용.
     * @param maxItems Android 다중 선택 최대 개수(기본 30).
     */
    fun launchPhotoPicker(
        allowMultiple: Boolean = true,
        maxItems: Int = 30,
    )
}

/** picker 가 반환한 사진 한 장. */
data class PickedPhoto(
    val uri: String,
    val name: String = "",
)

package com.eunilsung.talk.data.local

import kotlinx.coroutines.flow.SharedFlow

/** 임의 파일(.docx, .xlsx, .zip 등)을 선택하는 플랫폼별 native picker. */
interface FilePickerProvider {
    /** 사용자가 선택한 파일들의 [PickedFile] 리스트를 emit 하는 결과 stream. */
    val results: SharedFlow<List<PickedFile>>

    /** 마지막으로 picker 를 실행한 요청자 태그. */
    val lastRequestId: String

    /** Picker 실행. 결과는 [results] 로 비동기 emit. */
    fun launchFilePicker(
        requestId: String,
        allowMultiple: Boolean = true,
        mimeTypes: List<String> = listOf("*/*"),
    )
}

/** picker 요청자 태그 생성기. */
object FilePickerRequestId {
    private var seq = 0
    fun next(prefix: String): String = "$prefix-${++seq}"
}

/** picker 결과 — 플랫폼별 URI 와 표시용 파일명, byte 크기(모르면 -1). */
data class PickedFile(
    val uri: String,
    val name: String,
    val sizeBytes: Long = -1L,
)

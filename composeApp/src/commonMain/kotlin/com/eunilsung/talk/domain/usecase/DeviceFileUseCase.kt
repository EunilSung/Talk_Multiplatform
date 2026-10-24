package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.OpenFileResult
import com.eunilsung.talk.domain.repository.DeviceFileRepository

/** 받은 파일을 다운로드 폴더에 저장한다. 저장했으면 true. */
class DownloadFileUseCase(private val repository: DeviceFileRepository) {
    suspend operator fun invoke(source: String, displayName: String): Boolean =
        repository.saveToDownloads(source, displayName) != null
}

/**
 * 받은 파일을 연다. 앱 캐시의 사본으로만 연다 — 다운로드 폴더(파일 앱)에는 저장하지 않는다.
 * 공용 폴더 저장은 사용자가 '저장'을 눌렀을 때만 한다([DownloadFileUseCase]).
 */
class OpenFileUseCase(private val repository: DeviceFileRepository) {
    suspend operator fun invoke(source: String, displayName: String): OpenFileResult {
        val path = repository.copyForOpen(source, displayName) ?: return OpenFileResult.FAILED
        return if (repository.open(path)) OpenFileResult.OPENED else OpenFileResult.FAILED
    }
}

/** 받은 사진·동영상을 사진첩에 저장한다. 저장했으면 true. */
class SaveToGalleryUseCase(private val repository: DeviceFileRepository) {
    suspend operator fun invoke(source: String, saveAs: String, isVideo: Boolean): Boolean =
        repository.saveToGallery(source, saveAs, isVideo)
}

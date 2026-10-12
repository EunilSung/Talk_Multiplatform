package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.OpenFileResult
import com.eunilsung.talk.domain.repository.DeviceFileRepository

/** 받은 파일을 다운로드 폴더에 저장한다. 저장했으면 true. */
class DownloadFileUseCase(private val repository: DeviceFileRepository) {
    suspend operator fun invoke(source: String, displayName: String): Boolean =
        repository.saveToDownloads(source, displayName) != null
}

/** 받은 파일을 연다. 받아 둔 파일이 없으면 먼저 다운로드 폴더에 받는다. */
class OpenFileUseCase(private val repository: DeviceFileRepository) {
    suspend operator fun invoke(source: String, displayName: String): OpenFileResult {
        val existing = repository.findDownloaded(displayName)
        val path = existing ?: repository.saveToDownloads(source, displayName)
            ?: return OpenFileResult.FAILED
        if (!repository.open(path)) return OpenFileResult.FAILED
        return if (existing == null) OpenFileResult.DOWNLOADED_AND_OPENED else OpenFileResult.OPENED
    }
}

/** 받은 사진·동영상을 사진첩에 저장한다. 저장했으면 true. */
class SaveToGalleryUseCase(private val repository: DeviceFileRepository) {
    suspend operator fun invoke(source: String, saveAs: String, isVideo: Boolean): Boolean =
        repository.saveToGallery(source, saveAs, isVideo)
}

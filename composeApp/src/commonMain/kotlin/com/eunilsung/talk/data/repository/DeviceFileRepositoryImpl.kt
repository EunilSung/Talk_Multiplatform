package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.local.FileMetadataResolver
import com.eunilsung.talk.data.local.FileOpener
import com.eunilsung.talk.domain.repository.DeviceFileRepository

/** 플랫폼 파일 API([FileMetadataResolver]·[FileOpener]) 위에서 받은 파일을 저장하고 연다. 실패는 모두 null·false 로 돌려준다. */
class DeviceFileRepositoryImpl(
    private val resolver: FileMetadataResolver,
    private val opener: FileOpener,
) : DeviceFileRepository {

    override suspend fun saveToDownloads(source: String, displayName: String): String? {
        val bytes = runCatching { resolver.readBytes(source) }.getOrNull() ?: return null
        return runCatching { resolver.saveDownloadedFile(displayName, bytes) }.getOrNull()
    }

    /**
     * 사본은 원본 경로마다 다른 하위 폴더에 둔다. 이름이 같은 다른 파일을 열어도 먼저 연 사본을 덮어쓰지 않는다.
     * 이름이 비면 원본 경로의 마지막 조각을 쓴다(확장자로 열 앱을 고르기 때문이다).
     */
    override suspend fun copyForOpen(source: String, displayName: String): String? {
        val bytes = runCatching { resolver.readBytes(source) }.getOrNull() ?: return null
        val name = displayName.ifBlank { source.substringAfterLast('/') }
        val key = source.hashCode().toUInt().toString(KEY_RADIX)
        return runCatching { resolver.writeOpenCopy(key, name, bytes) }.getOrNull()
    }

    override suspend fun open(path: String): Boolean =
        runCatching { opener.open(path) }.getOrDefault(false)

    override suspend fun saveToGallery(source: String, saveAs: String, isVideo: Boolean): Boolean {
        val bytes = runCatching { resolver.readBytes(source) }.getOrNull() ?: return false
        val saved = runCatching {
            if (isVideo) resolver.saveVideoToGallery(saveAs, bytes) else resolver.saveToGallery(saveAs, bytes)
        }.getOrNull()
        return saved != null
    }

    private companion object {
        const val KEY_RADIX = 16
    }
}

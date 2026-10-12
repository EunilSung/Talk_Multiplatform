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

    override suspend fun findDownloaded(displayName: String): String? =
        runCatching { resolver.findDownloadedFile(displayName) }.getOrNull()

    override suspend fun open(path: String): Boolean =
        runCatching { opener.open(path) }.getOrDefault(false)

    override suspend fun saveToGallery(source: String, saveAs: String, isVideo: Boolean): Boolean {
        val bytes = runCatching { resolver.readBytes(source) }.getOrNull() ?: return false
        val saved = runCatching {
            if (isVideo) resolver.saveVideoToGallery(saveAs, bytes) else resolver.saveToGallery(saveAs, bytes)
        }.getOrNull()
        return saved != null
    }
}

package com.eunilsung.talk.testsupport

import com.eunilsung.talk.data.local.FileMetadata
import com.eunilsung.talk.data.local.FileMetadataResolver

/**
 * 기기 파일 대역 — 경로와 바이트를 메모리에 들고 있다.
 *
 * [put] 으로 "기기에 있는 파일"을 만들어 두면 저장소가 그것을 읽어 올린다. 저장소가 내려받아
 * 캐시에 쓴 파일은 `/cache/이름` 경로로 [files] 에 들어온다.
 */
class FakeFileMetadataResolver : FileMetadataResolver {

    val files = mutableMapOf<String, ByteArray>()

    fun put(path: String, bytes: ByteArray) {
        files[path] = bytes
    }

    override suspend fun resolve(path: String): FileMetadata {
        val name = path.substringAfterLast('/')
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        return FileMetadata(name, extension, IMAGE_SIZE, files[path]?.size?.toLong() ?: -1L)
    }

    override suspend fun readBytes(path: String): ByteArray? = files[path]

    override suspend fun writeCacheFile(filename: String, bytes: ByteArray): String =
        "/cache/$filename".also { files[it] = bytes }

    override suspend fun saveDownloadedFile(filename: String, bytes: ByteArray): String? = null
    override suspend fun saveToGallery(filename: String, bytes: ByteArray): String? = null
    override suspend fun saveVideoToGallery(filename: String, bytes: ByteArray): String? = null
    override suspend fun findDownloadedFile(filename: String): String? = null

    companion object {
        const val IMAGE_SIZE = "100:200"
    }
}

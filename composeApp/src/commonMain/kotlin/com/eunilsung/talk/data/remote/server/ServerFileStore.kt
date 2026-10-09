package com.eunilsung.talk.data.remote.server

import com.eunilsung.talk.data.local.FileMetadataResolver
import com.russhwolf.settings.Settings

/**
 * 서버 파일과 기기 안 경로를 잇는 보관소.
 *
 * 화면은 말풍선의 파일을 기기 안 경로로 연다(사진 보기·동영상 재생·저장 모두). 그래서 서버의 파일은
 * 내려받아 캐시에 두고, "이 파일 id 는 이 경로에 있다"를 적어 둔다. 내가 올린 파일은 원본 경로를
 * 그대로 적어 두어 다시 내려받지 않는다.
 */
class ServerFileStore(
    private val settings: Settings,
    private val server: TalkServer,
    private val fileMetadataResolver: FileMetadataResolver,
) {

    /** 이 파일이 기기 어디에 있는지. 아직 받지 않았으면 null. */
    fun localPath(fileId: String): String? = settings.getStringOrNull(keyOf(fileId))

    fun remember(fileId: String, path: String) {
        if (fileId.isBlank() || path.isBlank()) return
        settings.putString(keyOf(fileId), path)
    }

    /**
     * 파일을 내려받아 캐시에 두고 그 경로를 돌려준다. 이미 있으면 받지 않는다. 실패하면 null.
     *
     * 캐시 파일 이름에 id 를 붙인다. 같은 이름의 파일이 여러 번 와도 서로 덮어쓰지 않는다.
     */
    suspend fun download(fileId: String, fileName: String): String? {
        localPath(fileId)?.let { return it }
        val bytes = server.downloadFile(fileId).valueOrNull() ?: return null
        val path = fileMetadataResolver.writeCacheFile("${fileId.take(ID_PREFIX_LENGTH)}_$fileName", bytes) ?: return null
        remember(fileId, path)
        return path
    }

    private fun keyOf(fileId: String): String = "server_file_$fileId"

    private companion object {
        const val ID_PREFIX_LENGTH = 8
    }
}

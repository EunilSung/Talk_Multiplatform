package com.eunilsung.talk.data.remote.server

import com.eunilsung.talk.data.local.FileMetadataResolver
import com.eunilsung.talk.util.sanitizeFileName
import com.russhwolf.settings.Settings

/**
 * 서버 파일과 기기 안 경로를 잇는 보관소.
 *
 * 화면은 말풍선의 파일을 기기 안 경로로 연다(사진 보기·동영상 재생·저장 모두). 그래서 서버의 파일은
 * 내려받아 캐시에 두고, "이 파일 id 는 이 경로에 있다"를 적어 둔다. 내가 올린 파일은 원본 경로를
 * 그대로 적어 두어 다시 내려받지 않는다.
 *
 * 서버가 확정한 파일 대화마다 "이 방의 이 대화는 이 파일 id 다"도 적어 둔다. 같은 기기 파일을 여러 방에 보내거나
 * 전달하면 한 경로를 id 여럿이 가리켜, 경로만으로는 어느 방의 파일인지 알 수 없기 때문이다.
 */
class ServerFileStore(
    private val settings: Settings,
    private val server: TalkServer,
    private val fileMetadataResolver: FileMetadataResolver,
) {

    /** 이 파일이 기기 어디에 있는지. 아직 받지 않았으면 null. */
    fun localPath(fileId: String): String? = settings.getStringOrNull(keyOf(fileId))

    /**
     * 기기 [path] 에 둔 파일의 서버 id. 적어 둔 것이 없으면(아직 받지 않았으면) null.
     *
     * 적어 둔 것을 모두 훑는다. 같은 파일을 여러 방에 보내거나 전달하면 id 여럿이 한 경로를 가리키는데, 그때는 그중 하나다.
     * 그래서 대화를 알면 [chatFileId] 를 먼저 쓴다. 이 함수는 대화 기록이 없는 대화(기록을 남기기 전에 받은 것)에만 쓴다.
     */
    fun fileIdOf(path: String): String? {
        if (path.isBlank()) return null
        return settings.keys
            .firstOrNull { it.startsWith(KEY_PREFIX) && settings.getStringOrNull(it) == path }
            ?.removePrefix(KEY_PREFIX)
    }

    fun remember(fileId: String, path: String) {
        if (fileId.isBlank() || path.isBlank()) return
        settings.putString(keyOf(fileId), path)
    }

    /** 방 [chatRoomId] 의 대화 [chatId] 가 담은 서버 파일 id. 서버가 확정한 그 대화를 받은 적이 없으면 null. */
    fun chatFileId(chatRoomId: String, chatId: String): String? {
        if (chatRoomId.isBlank() || chatId.isBlank()) return null
        return settings.getStringOrNull(chatKeyOf(chatRoomId, chatId))
    }

    /**
     * 서버가 확정한 파일 대화의 서버 파일 id 를 적어 둔다.
     *
     * 대화 id 는 방 안에서만 유일하므로(서버가 방마다 따로 받는다) 방 id 와 함께 적는다.
     */
    fun rememberChatFile(chatRoomId: String, chatId: String, fileId: String) {
        if (chatRoomId.isBlank() || chatId.isBlank() || fileId.isBlank()) return
        settings.putString(chatKeyOf(chatRoomId, chatId), fileId)
    }

    /**
     * 파일을 내려받아 캐시에 두고 그 경로를 돌려준다. 이미 있으면 받지 않는다. 실패하면 null.
     *
     * 캐시 파일 이름에 id 를 붙인다. 같은 이름의 파일이 여러 번 와도 서로 덮어쓰지 않는다.
     * [fileName] 은 보낸 사람이 정한 값이라 마지막 경로 조각으로 정리한 뒤에 붙인다([sanitizeFileName]) —
     * `../` 로 id 접두어를 떼어 내 다른 파일의 캐시를 덮어쓰지 못하게 한다.
     */
    suspend fun download(fileId: String, fileName: String): String? {
        localPath(fileId)?.let { return it }
        val bytes = server.downloadFile(fileId).valueOrNull() ?: return null
        val cacheName = "${sanitizeFileName(fileId.take(ID_PREFIX_LENGTH))}_${sanitizeFileName(fileName)}"
        val path = fileMetadataResolver.writeCacheFile(cacheName, bytes) ?: return null
        remember(fileId, path)
        return path
    }

    private fun keyOf(fileId: String): String = "$KEY_PREFIX$fileId"

    private fun chatKeyOf(chatRoomId: String, chatId: String): String = "$CHAT_KEY_PREFIX$chatRoomId/$chatId"

    private companion object {
        const val ID_PREFIX_LENGTH = 8
        const val KEY_PREFIX = "server_file_"
        const val CHAT_KEY_PREFIX = "server_chat_file_"
    }
}

package com.eunilsung.talk.server.files

import java.io.File

/**
 * 올라온 파일의 바이트를 디스크에 둔다.
 *
 * 파일 이름은 서버가 만든 id 만 쓴다. 사용자가 보낸 이름을 경로에 쓰면 `../` 같은 값으로 폴더 밖을
 * 건드릴 수 있다. 원래 이름은 DB 에 따로 적어 두고 내려줄 때 붙인다.
 */
class FileStorage(private val directory: File) {

    init {
        directory.mkdirs()
    }

    fun save(id: String, bytes: ByteArray) {
        fileOf(id).writeBytes(bytes)
    }

    fun read(id: String): ByteArray? = fileOf(id).takeIf { it.isFile }?.readBytes()

    fun delete(id: String) {
        fileOf(id).delete()
    }

    /** id 는 서버가 만든 UUID 뿐이다. 그 모양이 아니면 받지 않는다 — 경로 조작을 한 번 더 막는다. */
    private fun fileOf(id: String): File {
        require(ID_PATTERN.matches(id)) { "잘못된 파일 id" }
        return File(directory, id)
    }

    private companion object {
        val ID_PATTERN = Regex("[0-9a-fA-F-]{36}")
    }
}

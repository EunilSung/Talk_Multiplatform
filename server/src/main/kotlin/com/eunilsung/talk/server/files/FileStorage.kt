package com.eunilsung.talk.server.files

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 올라온 파일의 바이트를 디스크에 둔다.
 *
 * 파일 이름은 서버가 만든 id 만 쓴다. 사용자가 보낸 이름을 경로에 쓰면 `../` 같은 값으로 폴더 밖을
 * 건드릴 수 있다. 원래 이름은 DB 에 따로 적어 두고 내려줄 때 붙인다.
 *
 * 바이트를 통째로 메모리에 올리지 않는다. 올리기는 받는 대로 임시 파일에 쓰고, 받기와 전달은 파일을 그대로 흘려보낸다 —
 * 20MB 파일 몇 개를 동시에 다루는 것만으로 작은 힙이 차지 않게 한다.
 */
class FileStorage(private val directory: File) {

    /** 다 받기 전의 파일을 두는 곳. id 모양이 아닌 이름이라 [fileOf] 로는 닿지 않는다. */
    private val incomingDirectory = File(directory, INCOMING_DIRECTORY)

    init {
        directory.mkdirs()
        incomingDirectory.mkdirs()
    }

    /** 받는 중인 파일. 다 받았으면 [keep] 으로 id 에 붙이고, 쓰지 않을 거면 [discard] 로 버린다. */
    class Incoming internal constructor(internal val file: File, val size: Long)

    /**
     * [source] 를 끝까지 읽어 임시 파일에 쓴다. [maxBytes] 를 넘으면 쓰던 것을 지우고 null 을 돌려준다.
     *
     * 읽기는 기다리는 동안 스레드를 놓는다(suspend). 디스크 쓰기만 IO 쪽에서 한다. 읽는 동안 스레드를 붙잡으면, 몇 초에
     * 한 바이트씩 보내는 올리기 몇십 개로 모든 DB 조회가 쓰는 IO 스레드가 묶여 서버 전체가 멈춘다.
     * 읽다가 끊기거나 취소되면(시간 제한) 쓰던 것을 지우고 그 예외를 그대로 던진다.
     */
    suspend fun receive(source: ByteReadChannel, maxBytes: Long): Incoming? {
        val temp = File(incomingDirectory, UUID.randomUUID().toString())
        try {
            var size = 0L
            withContext(Dispatchers.IO) { temp.outputStream() }.use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val read = source.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    size += read
                    if (size > maxBytes) break
                    withContext(Dispatchers.IO) { output.write(buffer, 0, read) }
                }
            }
            if (size > maxBytes) {
                temp.delete()
                return null
            }
            return Incoming(temp, size)
        } catch (cause: Throwable) {
            temp.delete()
            throw cause
        }
    }

    fun keep(incoming: Incoming, id: String) {
        val target = fileOf(id)
        if (!incoming.file.renameTo(target)) {
            incoming.file.copyTo(target, overwrite = true)
            incoming.file.delete()
        }
    }

    fun discard(incoming: Incoming) {
        incoming.file.delete()
    }

    /** [sourceId] 의 바이트를 [targetId] 로 복사한다. 원본이 없으면 false. */
    fun copy(sourceId: String, targetId: String): Boolean {
        val source = fileOf(sourceId).takeIf { it.isFile } ?: return false
        source.copyTo(fileOf(targetId), overwrite = true)
        return true
    }

    /** 저장된 파일. 없으면 null. 응답으로 흘려보낼 때 쓴다. */
    fun stored(id: String): File? = fileOf(id).takeIf { it.isFile }

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
        const val INCOMING_DIRECTORY = "incoming"
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}

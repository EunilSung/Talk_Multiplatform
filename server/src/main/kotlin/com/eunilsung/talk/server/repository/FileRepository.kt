package com.eunilsung.talk.server.repository

import com.eunilsung.talk.server.limits.ServerLimits
import com.eunilsung.talk.shared.api.FileDto
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

/** 파일을 기록한 결과. */
sealed interface FileRegistration {
    data class Registered(val file: FileDto) : FileRegistration

    /** 방이 없거나 참여 중이 아니다. */
    data object RoomNotFound : FileRegistration

    /** 이 파일을 더하면 하루 한도([ServerLimits.fileUploadsPerDay]·[ServerLimits.fileBytesPerDay])를 넘는다. */
    data object QuotaExceeded : FileRegistration
}

/**
 * 파일 기록 저장소 — 누가 어느 방에 무엇을 올렸는지.
 *
 * 올리기와 받기 모두 "그 방에 참여 중인가"를 여기서 확인한다. 참여자가 아니면 없는 파일과 똑같이
 * null 을 돌려준다. 하루 한도도 여기서 지킨다 — 올리기와 전달 복사가 모두 [register] 를 거친다.
 */
class FileRepository(
    private val dataSource: DataSource,
    private val limits: ServerLimits = ServerLimits(),
) {

    /** 한 사람이 최근 24시간 동안 올리거나 전달로 복사한 파일의 개수와 바이트. */
    private data class Usage(val count: Int, val bytes: Long)

    /**
     * 파일을 기록하고 id 를 내준다. [name] 은 이미 정리된 이름이어야 한다.
     *
     * 하루 한도 확인과 기록을 한 트랜잭션에서 올린 사람별 잠금을 잡고 한다. 따로 하면 같은 계정의 동시 요청 수백 개가
     * 모두 같은 "오늘 사용량"을 읽고 통과해, 한 번에 (동시 요청 수 × 파일 크기) 만큼 한도를 넘긴다.
     */
    fun register(roomId: String, uploaderId: String, name: String, size: Long): FileRegistration = transaction { conn ->
        conn.prepareStatement("SELECT pg_advisory_xact_lock(?, hashtext(?))").use { st ->
            st.setInt(1, QUOTA_LOCK_NAMESPACE)
            st.setString(2, uploaderId)
            st.executeQuery().close()
        }
        if (!fits(usageToday(conn, uploaderId), size)) return@transaction FileRegistration.QuotaExceeded
        val id = UUID.randomUUID().toString()
        val inserted = conn.prepareStatement(
            """
            INSERT INTO chat_file (id, room_id, uploader_id, name, size)
            SELECT ?, m.room_id, m.user_id, ?, ?
            FROM chat_room_member m
            WHERE m.room_id = ? AND m.user_id = ? AND m.left_at IS NULL
            """.trimIndent()
        ).use { st ->
            st.setString(1, id)
            st.setString(2, name)
            st.setLong(3, size)
            st.setString(4, roomId)
            st.setString(5, uploaderId)
            st.executeUpdate() > 0
        }
        if (inserted) FileRegistration.Registered(FileDto(id, name, size)) else FileRegistration.RoomNotFound
    }

    /**
     * [size] 바이트를 더 올려도 지금 하루 한도 안인지. 잠그지 않고 본다 — 큰 본문을 받기 전에 이미 한도를 다 쓴 요청을
     * 일찍 돌려보내는 데만 쓰고, 실제로 지키는 것은 [register] 다.
     */
    fun hasQuotaFor(uploaderId: String, size: Long): Boolean =
        dataSource.connection.use { conn -> fits(usageToday(conn, uploaderId), size) }

    /**
     * 내가 받을 수 있는 파일. 없는 파일이거나, 그 방의 참여자가 아니거나, 내가 들어오기 전 대화의 파일이면 null.
     *
     * 방에 참여 중인 것만으로는 주지 않는다. 그 파일을 실은 대화가 내가 볼 수 있는 구간에 있어야 한다 — 나중에 초대받은
     * 사람이 그 전 대화의 첨부를 id 로 꺼내 가지 못하게 한다. 내가 올린 파일은 대화에 싣기 전이어도 받을 수 있다.
     */
    fun find(fileId: String, userId: String): FileDto? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT f.id, f.name, f.size
                FROM chat_file f
                JOIN chat_room_member m ON m.room_id = f.room_id
                WHERE f.id = ? AND m.user_id = ? AND m.left_at IS NULL
                  AND (f.uploader_id = m.user_id OR EXISTS (
                      SELECT 1 FROM chat_message g
                      WHERE g.room_id = f.room_id AND g.file_id = f.id AND g.seq > m.joined_seq
                  ))
                """.trimIndent()
            ).use { st ->
                st.setString(1, fileId)
                st.setString(2, userId)
                st.executeQuery().use { rs ->
                    if (rs.next()) FileDto(rs.getString(1), rs.getString(2), rs.getLong(3)) else null
                }
            }
        }

    /**
     * 파일 기록을 지운다. 지웠으면 true — 그때만 디스크의 바이트도 지운다.
     *
     * 그 방([roomId])에 [uploaderId] 가 올린 파일이고, 회수되지 않은 대화가 그 파일을 싣고 있지 않을 때만 지운다.
     * 부르는 쪽이 확인을 빠뜨려도 남이 올린 파일이나 다른 대화가 쓰는 파일은 지워지지 않는다.
     */
    fun deleteUnused(fileId: String, roomId: String, uploaderId: String): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                DELETE FROM chat_file f
                WHERE f.id = ? AND f.room_id = ? AND f.uploader_id = ?
                  AND NOT EXISTS (SELECT 1 FROM chat_message g WHERE g.file_id = f.id AND g.recalled_at IS NULL)
                """.trimIndent()
            ).use { st ->
                st.setString(1, fileId)
                st.setString(2, roomId)
                st.setString(3, uploaderId)
                st.executeUpdate() > 0
            }
        }

    /** [uploaderId] 가 최근 24시간 동안 올리거나 전달로 복사한 파일. 하루 한도를 셀 때 쓴다. */
    private fun usageToday(conn: Connection, uploaderId: String): Usage =
        conn.prepareStatement(
            """
            SELECT count(*), coalesce(sum(size), 0) FROM chat_file
            WHERE uploader_id = ? AND created_at > now() - interval '1 day'
            """.trimIndent()
        ).use { st ->
            st.setString(1, uploaderId)
            st.executeQuery().use { rs ->
                rs.next()
                Usage(rs.getInt(1), rs.getLong(2))
            }
        }

    /** 오늘 쓴 것([usage])에 [size] 바이트 파일 하나를 더해도 하루 한도 안인지. */
    private fun fits(usage: Usage, size: Long): Boolean =
        usage.count < limits.fileUploadsPerDay && usage.bytes + size <= limits.fileBytesPerDay

    private fun <T> transaction(block: (Connection) -> T): T =
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                block(conn).also { conn.commit() }
            } catch (cause: Throwable) {
                conn.rollback()
                throw cause
            } finally {
                conn.autoCommit = true
            }
        }

    private companion object {
        /** 하루 한도 잠금의 이름 공간. 다른 용도의 권고 잠금(pg_advisory_xact_lock)과 겹치지 않게 첫 키로 쓴다. */
        const val QUOTA_LOCK_NAMESPACE = 7001
    }
}

package com.eunilsung.talk.server.repository

import com.eunilsung.talk.shared.api.FileDto
import java.util.UUID
import javax.sql.DataSource

/**
 * 파일 기록 저장소 — 누가 어느 방에 무엇을 올렸는지.
 *
 * 올리기와 받기 모두 "그 방에 참여 중인가"를 여기서 확인한다. 참여자가 아니면 없는 파일과 똑같이
 * null 을 돌려준다.
 */
class FileRepository(private val dataSource: DataSource) {

    /** 파일을 기록하고 id 를 내준다. 방에 참여 중이 아니면 null. */
    fun register(roomId: String, uploaderId: String, name: String, size: Long): FileDto? =
        dataSource.connection.use { conn ->
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
            if (inserted) FileDto(id, name, size) else null
        }

    /** 내가 받을 수 있는 파일. 없는 파일이거나 그 방의 참여자가 아니면 null. */
    fun find(fileId: String, userId: String): FileDto? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT f.id, f.name, f.size
                FROM chat_file f
                JOIN chat_room_member m ON m.room_id = f.room_id
                WHERE f.id = ? AND m.user_id = ? AND m.left_at IS NULL
                """.trimIndent()
            ).use { st ->
                st.setString(1, fileId)
                st.setString(2, userId)
                st.executeQuery().use { rs ->
                    if (rs.next()) FileDto(rs.getString(1), rs.getString(2), rs.getLong(3)) else null
                }
            }
        }

    /** 이 파일이 이 방에 올라온 것인지. 다른 방의 파일 id 를 대화에 끼워 넣지 못하게 막는 데 쓴다. */
    fun belongsTo(fileId: String, roomId: String): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM chat_file WHERE id = ? AND room_id = ?").use { st ->
                st.setString(1, fileId)
                st.setString(2, roomId)
                st.executeQuery().use { it.next() }
            }
        }

    fun delete(fileId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM chat_file WHERE id = ?").use { st ->
                st.setString(1, fileId)
                st.executeUpdate()
            }
        }
    }
}

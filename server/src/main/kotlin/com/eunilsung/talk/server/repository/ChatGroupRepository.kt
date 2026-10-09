package com.eunilsung.talk.server.repository

import com.eunilsung.talk.shared.api.ChatGroupDto
import javax.sql.DataSource

/**
 * 대화그룹 저장소 — 대화함 위쪽의 칩과 그 칩에 담긴 방.
 *
 * 그룹은 사람마다 따로 가진다. 앱은 그룹을 고칠 때마다 전체 목록을 보내고, 서버는 그것으로 통째로
 * 바꾼다. 그룹 수가 적고 한 사람만 고치는 값이라, 부분 변경을 따로 두는 것보다 이쪽이 어긋날 일이 적다.
 */
class ChatGroupRepository(private val dataSource: DataSource) {

    /** 내 그룹 전부, 순서대로. 내가 더 이상 참여하지 않는 방은 빠져서 나온다. */
    fun groups(userId: String): List<ChatGroupDto> = dataSource.connection.use { conn ->
        val roomsByGroup = conn.prepareStatement(
            """
            SELECT gr.group_id, gr.room_id
            FROM chat_group_room gr
            JOIN chat_room_member m ON m.room_id = gr.room_id AND m.user_id = gr.user_id AND m.left_at IS NULL
            WHERE gr.user_id = ?
            """.trimIndent()
        ).use { st ->
            st.setString(1, userId)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) }
            }
        }.groupBy({ it.first }, { it.second })

        conn.prepareStatement("SELECT id, name, sort FROM chat_group WHERE user_id = ? ORDER BY sort, id").use { st ->
            st.setString(1, userId)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val id = rs.getString(1)
                        add(ChatGroupDto(id, rs.getString(2), rs.getInt(3), roomsByGroup[id].orEmpty()))
                    }
                }
            }
        }
    }

    /**
     * 내 그룹을 [groups] 로 통째로 바꾼다.
     *
     * 내가 참여하지 않는 방의 id 는 조용히 버린다. 남의 방 id 를 끼워 넣어도 아무 일도 일어나지 않게 한다.
     */
    fun replace(userId: String, groups: List<ChatGroupDto>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM chat_group WHERE user_id = ?").use { st ->
                    st.setString(1, userId)
                    st.executeUpdate()
                }
                conn.prepareStatement("INSERT INTO chat_group (user_id, id, name, sort) VALUES (?, ?, ?, ?)").use { st ->
                    groups.forEach { group ->
                        st.setString(1, userId)
                        st.setString(2, group.id)
                        st.setString(3, group.name)
                        st.setInt(4, group.sort)
                        st.addBatch()
                    }
                    st.executeBatch()
                }
                conn.prepareStatement(
                    """
                    INSERT INTO chat_group_room (user_id, group_id, room_id)
                    SELECT ?, ?, m.room_id FROM chat_room_member m
                    WHERE m.user_id = ? AND m.room_id = ? AND m.left_at IS NULL
                    ON CONFLICT DO NOTHING
                    """.trimIndent()
                ).use { st ->
                    groups.forEach { group ->
                        group.roomIds.forEach { roomId ->
                            st.setString(1, userId)
                            st.setString(2, group.id)
                            st.setString(3, userId)
                            st.setString(4, roomId)
                            st.addBatch()
                        }
                    }
                    st.executeBatch()
                }
                conn.commit()
            } catch (cause: Throwable) {
                conn.rollback()
                throw cause
            } finally {
                conn.autoCommit = true
            }
        }
    }
}

package com.eunilsung.talk.server.repository

import com.eunilsung.talk.shared.api.ContactGroupDto
import javax.sql.DataSource

/**
 * 내그룹 저장소 — 사람마다 따로 가지는 연락처 묶음.
 *
 * 앱은 그룹을 고칠 때마다 전체 목록을 보내고, 서버는 그것으로 통째로 바꾼다.
 */
class ContactGroupRepository(private val dataSource: DataSource) {

    /** 내 그룹 전부, 순서대로. */
    fun groups(userId: String): List<ContactGroupDto> = dataSource.connection.use { conn ->
        val membersByGroup = conn.prepareStatement(
            """
            SELECT gm.group_id, gm.member_id
            FROM contact_group_member gm JOIN app_user u ON u.id = gm.member_id
            WHERE gm.user_id = ?
            ORDER BY u.position_sort, u.name
            """.trimIndent()
        ).use { st ->
            st.setString(1, userId)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) }
            }
        }.groupBy({ it.first }, { it.second })

        conn.prepareStatement("SELECT id, name, sort FROM contact_group WHERE user_id = ? ORDER BY sort, id").use { st ->
            st.setString(1, userId)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val id = rs.getString(1)
                        add(ContactGroupDto(id, rs.getString(2), rs.getInt(3), membersByGroup[id].orEmpty()))
                    }
                }
            }
        }
    }

    /**
     * 내 그룹을 [groups] 로 통째로 바꾼다.
     *
     * 없는 사용자의 id 와 나 자신은 조용히 버린다. 그룹에 자기 자신을 담을 이유가 없고, 없는 id 를
     * 남겨 두면 화면에 이름 없는 사람이 생긴다.
     */
    fun replace(userId: String, groups: List<ContactGroupDto>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM contact_group WHERE user_id = ?").use { st ->
                    st.setString(1, userId)
                    st.executeUpdate()
                }
                conn.prepareStatement("INSERT INTO contact_group (user_id, id, name, sort) VALUES (?, ?, ?, ?)").use { st ->
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
                    INSERT INTO contact_group_member (user_id, group_id, member_id)
                    SELECT ?, ?, u.id FROM app_user u WHERE u.id = ? AND u.id <> ?
                    ON CONFLICT DO NOTHING
                    """.trimIndent()
                ).use { st ->
                    groups.forEach { group ->
                        group.memberIds.forEach { memberId ->
                            st.setString(1, userId)
                            st.setString(2, group.id)
                            st.setString(3, memberId)
                            st.setString(4, userId)
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

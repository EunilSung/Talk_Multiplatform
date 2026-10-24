package com.eunilsung.talk.server.repository

import com.eunilsung.talk.shared.api.UserDto
import java.sql.ResultSet
import javax.sql.DataSource

/** 로그인 판정에 필요한 것 — 사용자와 그 비밀번호 해시. 해시는 서버 밖으로 나가지 않는다. */
data class UserCredential(
    val user: UserDto,
    val passwordHash: String,
)

/** 사용자 저장소. 아이디는 소문자로 통일해 대소문자 차이로 다른 사람이 되지 않게 한다. */
class UserRepository(private val dataSource: DataSource) {

    fun find(userId: String): UserDto? = findCredential(userId)?.user

    fun findCredential(userId: String): UserCredential? =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM app_user WHERE id = ?").use { st ->
                st.setString(1, normalize(userId))
                st.executeQuery().use { rs ->
                    if (rs.next()) UserCredential(rs.toUser(), rs.getString("password_hash")) else null
                }
            }
        }

    /** 전체 사용자, 직급이 높은 순 → 이름 순. */
    fun all(): List<UserDto> =
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT * FROM app_user ORDER BY position_sort, name").use { rs ->
                    buildList { while (rs.next()) add(rs.toUser()) }
                }
            }
        }

    /**
     * 비밀번호 해시를 바꾸고 그 사람의 로그인(토큰)을 모두 끊는다. 없는 아이디면 아무 일도 하지 않는다.
     *
     * 비밀번호를 바꿔도 예전 비밀번호로 받아 둔 토큰이 살아 있으면 바꾼 의미가 없다. 공개된 비밀번호로 들어온 사람의
     * 토큰도 함께 끊는다. 그 로그인에 묶인 푸시 토큰은 표의 연쇄 삭제로 함께 지워진다.
     */
    fun resetPassword(userId: String, passwordHash: String) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("UPDATE app_user SET password_hash = ? WHERE id = ?").use { st ->
                    st.setString(1, passwordHash)
                    st.setString(2, normalize(userId))
                    st.executeUpdate()
                }
                conn.prepareStatement("DELETE FROM auth_token WHERE user_id = ?").use { st ->
                    st.setString(1, normalize(userId))
                    st.executeUpdate()
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

    /** 없을 때만 넣는다. 이미 있으면 그대로 둔다. */
    fun insertIfAbsent(user: UserDto, passwordHash: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO app_user
                    (id, password_hash, name, organ_name, position_name, position_sort,
                     email, phone_number, birthday, status_message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """.trimIndent()
            ).use { st ->
                st.setString(1, normalize(user.id))
                st.setString(2, passwordHash)
                st.setString(3, user.name)
                st.setString(4, user.organName)
                st.setString(5, user.positionName)
                st.setInt(6, user.positionSort)
                st.setString(7, user.email)
                st.setString(8, user.phoneNumber)
                st.setString(9, user.birthday)
                st.setString(10, user.statusMessage)
                st.executeUpdate()
            }
        }
    }

    private fun ResultSet.toUser() = UserDto(
        id = getString("id"),
        name = getString("name"),
        organName = getString("organ_name"),
        positionName = getString("position_name"),
        positionSort = getInt("position_sort"),
        email = getString("email"),
        phoneNumber = getString("phone_number"),
        birthday = getString("birthday"),
        statusMessage = getString("status_message"),
    )

    private fun normalize(userId: String): String = userId.trim().lowercase()
}

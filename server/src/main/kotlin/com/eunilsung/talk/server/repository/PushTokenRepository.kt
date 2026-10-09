package com.eunilsung.talk.server.repository

import javax.sql.DataSource

/** 푸시를 받을 기기 한 대. 배지에 쓸 안읽음 수가 사람마다 달라 [unreadTotal] 을 함께 들고 다닌다. */
data class PushTarget(
    val token: String,
    val platform: String,
    val userId: String,
    val unreadTotal: Int,
)

/** 푸시 토큰 보관. */
class PushTokenRepository(private val dataSource: DataSource) {

    /**
     * 토큰을 등록한다. 이미 있으면 주인을 덮어쓴다.
     *
     * 한 기기에서 로그아웃하고 다른 계정으로 들어와도 FCM 토큰은 그대로다. 주인을 갈지 않으면 그 기기로
     * 옛 계정의 대화가 계속 온다. [authTokenHash] 는 이 등록을 한 로그인이고, 그 로그인이 끝나면
     * 이 토큰도 함께 지워진다.
     */
    fun register(token: String, userId: String, platform: String, authTokenHash: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO push_token (token, user_id, platform, auth_token_hash, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (token) DO UPDATE
                    SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform,
                        auth_token_hash = EXCLUDED.auth_token_hash, updated_at = now()
                """.trimIndent()
            ).use { st ->
                st.setString(1, token)
                st.setString(2, userId)
                st.setString(3, platform)
                st.setString(4, authTokenHash)
                st.executeUpdate()
            }
        }
    }

    /** 푸시 서비스가 "없는 토큰"이라고 답했을 때 버린다. */
    fun delete(token: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM push_token WHERE token = ?").use { st ->
                st.setString(1, token)
                st.executeUpdate()
            }
        }
    }

    /**
     * 이 방의 새 대화를 알릴 기기들.
     *
     * 보낸 사람, 방을 나간 사람, 이 방의 알림을 끈 사람은 뺀다. 안읽음 수는 그 사람이 참여 중인
     * 모든 방의 합이다 — 앱 아이콘 배지에 그대로 쓰인다.
     */
    fun targetsForRoom(roomId: String, senderId: String): List<PushTarget> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT t.token, t.platform, m.user_id,
                       (SELECT coalesce(sum(greatest(r.next_seq - 1 - greatest(a.last_read_seq, a.joined_seq), 0)), 0)
                        FROM chat_room_member a JOIN chat_room r ON r.id = a.room_id
                        WHERE a.user_id = m.user_id AND a.left_at IS NULL) AS unread_total
                FROM chat_room_member m JOIN push_token t ON t.user_id = m.user_id
                WHERE m.room_id = ? AND m.left_at IS NULL AND NOT m.is_muted AND m.user_id <> ?
                """.trimIndent()
            ).use { st ->
                st.setString(1, roomId)
                st.setString(2, senderId)
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(PushTarget(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)))
                    }
                }
            }
        }
}

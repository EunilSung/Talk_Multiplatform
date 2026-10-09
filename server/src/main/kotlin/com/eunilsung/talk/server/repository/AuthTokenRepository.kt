package com.eunilsung.talk.server.repository

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.sql.DataSource

/**
 * 인증 토큰 저장소.
 *
 * 토큰은 한 번만 평문으로 존재한다 — 발급해 앱에 돌려주는 그 순간이다. 서버에는 해시만 남으므로
 * 이 표가 새어도 그것만으로는 남의 계정을 쓸 수 없고, 잃어버린 토큰을 다시 알려 줄 방법도 없다.
 */
class AuthTokenRepository(private val dataSource: DataSource) {

    private val random = SecureRandom()

    /**
     * 토큰을 새로 만들어 돌려준다.
     *
     * 기기마다 하나씩 쌓인다. 한 사람이 폰과 태블릿에서 각각 쓰는 것이 정상이라 이전 것을 지우지 않는다.
     */
    fun issue(userId: String): String {
        val raw = ByteArray(TOKEN_BYTES).also { random.nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT INTO auth_token (token_hash, user_id) VALUES (?, ?)").use { st ->
                st.setString(1, hash(token))
                st.setString(2, userId)
                st.executeUpdate()
            }
        }
        return token
    }

    /** 토큰이 가리키는 사람. 없으면 null. 마지막 사용 시각을 함께 찍어 오래 안 쓴 토큰을 가려낼 근거를 남긴다. */
    fun userIdOf(token: String): String? {
        if (token.isBlank()) return null
        return dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE auth_token SET last_used_at = now() WHERE token_hash = ? RETURNING user_id"
            ).use { st ->
                st.setString(1, hash(token))
                st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }
    }

    /** 로그아웃 — 이 토큰만 지운다. 다른 기기의 로그인은 그대로다. */
    fun revoke(token: String) {
        if (token.isBlank()) return
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM auth_token WHERE token_hash = ?").use { st ->
                st.setString(1, hash(token))
                st.executeUpdate()
            }
        }
    }

    private fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return Base64.getEncoder().encodeToString(digest)
    }

    private companion object {
        /** 32바이트면 무작위로 맞히는 것이 불가능하다. */
        const val TOKEN_BYTES = 32
    }
}

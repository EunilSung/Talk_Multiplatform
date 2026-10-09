package com.eunilsung.talk.server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 비밀번호 해시 — PBKDF2-HMAC-SHA512.
 *
 * 일부러 느린 함수를 쓴다. DB 가 새어 나가도 해시에서 비밀번호를 되찾는 데 드는 값이 커진다.
 * JDK 에 들어 있는 구현이라 의존성을 더하지 않는다.
 *
 * 저장 형식은 `pbkdf2-sha512$반복횟수$salt$hash` 다. 반복 횟수를 값 안에 적어 두므로,
 * 나중에 횟수를 올려도 예전에 만든 해시를 그대로 검증할 수 있다.
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA512"
    private const val SCHEME = "pbkdf2-sha512"
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val derived = derive(password, salt, ITERATIONS)
        val encoder = Base64.getEncoder()
        return listOf(SCHEME, ITERATIONS, encoder.encodeToString(salt), encoder.encodeToString(derived))
            .joinToString("$")
    }

    /** 형식이 깨진 값이면 틀린 것으로 본다 — 던지지 않는다. */
    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != SCHEME) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(password, salt, iterations), expected)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

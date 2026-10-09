package com.eunilsung.talk.server.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {

    @Test
    fun `만든 해시는 같은 비밀번호로만 맞는다`() {
        val stored = PasswordHasher.hash("1234")

        assertTrue(PasswordHasher.verify("1234", stored))
        assertFalse(PasswordHasher.verify("12345", stored))
        assertFalse(PasswordHasher.verify("", stored))
    }

    @Test
    fun `같은 비밀번호라도 해시는 매번 다르다`() {
        assertNotEquals(PasswordHasher.hash("1234"), PasswordHasher.hash("1234"))
    }

    @Test
    fun `형식이 깨진 값은 던지지 않고 틀린 것으로 본다`() {
        assertFalse(PasswordHasher.verify("1234", ""))
        assertFalse(PasswordHasher.verify("1234", "1234"))
        assertFalse(PasswordHasher.verify("1234", "pbkdf2-sha512${'$'}abc${'$'}###${'$'}###"))
        assertFalse(PasswordHasher.verify("1234", "bcrypt${'$'}10${'$'}c2FsdA==${'$'}aGFzaA=="))
    }
}

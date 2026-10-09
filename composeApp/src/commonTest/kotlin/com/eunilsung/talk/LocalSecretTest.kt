package com.eunilsung.talk

import com.eunilsung.talk.data.local.LocalSecret
import com.eunilsung.talk.data.local.LocalSecretCipher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 본문 잠그기의 **평문 호환** 규칙.
 *
 * 이 기능을 켜기 전에 저장된 대화는 평문 그대로 DB 에 남아 있다. 그 값을 못 읽으면 기존 사용자의
 * 대화가 통째로 빈칸이 된다. 접두어가 없는 값은 손대지 않고 그대로 돌려줘야 한다.
 *
 * 실제 잠금·풀기 왕복은 플랫폼 암호(Keystore·Keychain)가 필요해 `LocalSecretCipherTest` 에서 본다.
 */
class LocalSecretTest {

    @Test
    fun 잠기지_않은_예전_값은_그대로_읽는다() {
        val plain = "지난주에 저장된 대화 본문입니다"

        assertEquals(plain, LocalSecret.decrypt(plain))
        assertEquals(plain, LocalSecret.decryptOrNull(plain))
        assertFalse(LocalSecret.isSealed(plain))
    }

    @Test
    fun 빈_값은_잠그지_않는다() {
        assertEquals("", LocalSecret.encrypt(""))
        assertEquals("", LocalSecret.decrypt(""))
    }

    /**
     * 잠그지 못했을 때 평문으로 저장하면 잠그는 의미가 없고, 빈 값으로 저장하면 빈 본문이 캐시로 굳는다.
     * 풀 수 없는 값으로 저장해 평문이 남지 않게 한다.
     */
    @Test
    fun 잠그지_못하면_풀_수_없는_값으로_저장한다() {
        val previous = LocalSecret.cipher
        LocalSecret.cipher = object : LocalSecretCipher {
            override fun seal(plain: ByteArray): ByteArray? = null
            override fun open(payload: ByteArray): ByteArray? = null
        }
        try {
            val stored = LocalSecret.encrypt("잠글 수 없는 본문입니다")

            assertTrue(LocalSecret.isSealed(stored), "평문이 그대로 저장된다")
            assertFalse(stored.contains("본문"), "저장된 값에 원문이 남는다")
            assertNull(LocalSecret.decryptOrNull(stored), "풀 수 없는 값인데 캐시로 쓰인다")
        } finally {
            LocalSecret.cipher = previous
        }
    }

    @Test
    fun 접두어로_잠긴_값을_가린다() {
        assertTrue(LocalSecret.isSealed("enc1:abcd"))
        assertFalse(LocalSecret.isSealed("enc1 은 아니다"))
        assertFalse(LocalSecret.isSealed(""))
    }

    /** 뒤집기만 하는 가짜 암호 — 몇 번 풀었는지 센다. 호출마다 결과 앞에 다른 바이트를 붙여 IV 처럼 매번 다른 값을 만든다. */
    private class CountingCipher : LocalSecretCipher {
        var opens = 0
        private var nonce = 0
        override fun seal(plain: ByteArray): ByteArray = byteArrayOf((nonce++).toByte()) + plain.reversedArray()
        override fun open(payload: ByteArray): ByteArray? {
            opens++
            return payload.copyOfRange(1, payload.size).reversedArray()
        }
    }

    /**
     * 같은 잠긴 값은 **한 번만 푼다.** Android 는 풀 때마다 Keystore 를 부를 수 있어, 대화를 보낼 때마다 최신 30 행을 다시 푸느라
     * 보낸 말풍선이 늦게 뜰 수 있다. 방 입장 때 푼 값을 보낼 때 다시 읽으면 캐시에서 나와야 한다.
     */
    @Test
    fun 같은_잠긴_값은_한_번만_푼다() {
        val previous = LocalSecret.cipher
        val counting = CountingCipher()
        LocalSecret.cipher = counting
        try {
            val sealed = LocalSecret.encrypt("홍길동")
            val sealedAgain = LocalSecret.encrypt("홍길동")
            assertTrue(sealedAgain != sealed, "잠글 때마다 값이 달라야 한다")

            /** 같은 키로 앱을 다시 켠 상황 — 캐시가 비고, DB 에서 읽은 잠긴 값을 푼다. */
            LocalSecret.cipher = counting
            assertEquals("홍길동", LocalSecret.decrypt(sealed))
            assertEquals("홍길동", LocalSecret.decrypt(sealed))
            assertEquals("홍길동", LocalSecret.decryptOrNull(sealed))
            assertEquals(1, counting.opens, "같은 값을 다시 풀었다")

            assertEquals("홍길동", LocalSecret.decrypt(sealedAgain))
            assertEquals(2, counting.opens, "잠긴 값이 다르면 따로 푼다")
        } finally {
            LocalSecret.cipher = previous
        }
    }

    /** 키를 바꾸면(테스트의 가짜 키·키 유실) 앞 키로 푼 값이 캐시에서 나오면 안 된다. 못 푸는 값은 못 푸는 값이다. */
    @Test
    fun 암호를_바꾸면_캐시도_비운다() {
        val previous = LocalSecret.cipher
        LocalSecret.cipher = CountingCipher()
        try {
            val sealed = LocalSecret.encrypt("잠긴 본문")
            assertEquals("잠긴 본문", LocalSecret.decrypt(sealed))

            LocalSecret.cipher = object : LocalSecretCipher {
                override fun seal(plain: ByteArray): ByteArray? = null
                override fun open(payload: ByteArray): ByteArray? = null
            }

            assertNull(LocalSecret.decryptOrNull(sealed), "바뀐 키로 못 푸는 값을 캐시에서 꺼냈다")
        } finally {
            LocalSecret.cipher = previous
        }
    }

    /** 방금 잠근 값은 저장 뒤 곧바로 다시 읽힌다(목록 저장 → 다시 읽기). 원문을 알고 있으니 풀지 않는다. */
    @Test
    fun 방금_잠근_값은_풀지_않고_읽는다() {
        val previous = LocalSecret.cipher
        val counting = CountingCipher()
        LocalSecret.cipher = counting
        try {
            val sealed = LocalSecret.encrypt("대화방 이름")
            assertEquals("대화방 이름", LocalSecret.decrypt(sealed))
            assertEquals(0, counting.opens, "방금 잠근 값을 다시 풀었다")
        } finally {
            LocalSecret.cipher = previous
        }
    }
}

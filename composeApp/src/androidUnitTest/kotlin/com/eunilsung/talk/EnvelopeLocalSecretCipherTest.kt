package com.eunilsung.talk

import com.eunilsung.talk.data.local.DekStore
import com.eunilsung.talk.data.local.EnvelopeLocalSecretCipher
import com.eunilsung.talk.data.local.LocalSecretCipher
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Android 데이터 키 잠그기 — **칸마다 Keystore 를 부르지 않는지**, 예전 값과 키 파일을 지키는지.
 *
 * Keystore 는 JVM 단위 테스트에 없어 [FakeKek] 로 대신한다. 예전 Android 암호와 같은 형식(`[IV 12][암호문+태그]`)의
 * AES-GCM 을 고정 키로 돌리고, **몇 번 불렸는지 센다** — 실기기에서 느렸던 원인이 이 호출 수다(칸당 보안 칩 IPC).
 */
class EnvelopeLocalSecretCipherTest {

    private class FakeKek(private val ivOverride: ByteArray? = null) : LocalSecretCipher {
        private val key = SecretKeySpec(ByteArray(32) { (it * 3 + 1).toByte() }, "AES")
        var seals = 0
        var opens = 0
        var failOpen = false

        override fun seal(plain: ByteArray): ByteArray {
            seals++
            val iv = ivOverride ?: ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            return iv + cipher.doFinal(plain)
        }

        override fun open(payload: ByteArray): ByteArray? {
            opens++
            if (failOpen || payload.size <= 12) return null
            return runCatching {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload, 0, 12))
                cipher.doFinal(payload, 12, payload.size - 12)
            }.getOrNull()
        }
    }

    private class MemoryDekStore(var ready: Boolean = true) : DekStore {
        var stored: ByteArray? = null
        var creates = 0
        override fun isReady() = ready
        override fun read(): ByteArray? = stored
        override fun create(bytes: ByteArray): Boolean {
            if (stored != null) return false
            creates++
            stored = bytes.copyOf()
            return true
        }
    }

    private fun String.bytes() = encodeToByteArray()

    private fun ByteArray.startsWithMagic() =
        EnvelopeLocalSecretCipher.MAGIC.indices.all { this[it] == EnvelopeLocalSecretCipher.MAGIC[it] }

    @Test
    fun 새로_잠근_값은_데이터_키로_잠기고_다시_풀린다() {
        val cipher = EnvelopeLocalSecretCipher(FakeKek(), MemoryDekStore())

        val sealed = assertNotNull(cipher.seal("회의실 3층".bytes()))

        assertTrue(sealed.startsWithMagic(), "표식이 없다 — 읽을 때 예전 방식(Keystore)으로 푼다")
        assertTrue(cipher.isCurrent(sealed))
        assertEquals("회의실 3층", cipher.open(sealed)?.decodeToString())
    }

    /** 실기기에서 느렸던 원인 — 칸 수백 개에 Keystore 를 수백 번 불렀다. 이제는 데이터 키를 만들 때 한 번뿐이다. */
    @Test
    fun 칸을_아무리_많이_잠가도_Keystore_는_데이터_키에만_한_번_쓴다() {
        val kek = FakeKek()
        val cipher = EnvelopeLocalSecretCipher(kek, MemoryDekStore())

        repeat(500) { i ->
            val sealed = assertNotNull(cipher.seal("사용자 $i".bytes()))
            assertEquals("사용자 $i", cipher.open(sealed)?.decodeToString())
        }

        assertEquals(1, kek.seals, "칸마다 Keystore 로 잠갔다")
        assertEquals(0, kek.opens, "칸마다 Keystore 로 풀었다")
    }

    @Test
    fun 예전_방식으로_잠긴_값도_풀린다() {
        val kek = FakeKek()
        val legacy = kek.seal("예전 본문".bytes())
        val cipher = EnvelopeLocalSecretCipher(kek, MemoryDekStore())

        assertEquals("예전 본문", cipher.open(legacy)?.decodeToString(), "업데이트 전 저장된 값을 못 읽는다")
        assertFalse(cipher.isCurrent(legacy), "예전 값을 현재 방식으로 봤다 — 목록을 다시 받아도 영영 느린 값이 남는다")
    }

    /** 앱을 다시 켜면 같은 데이터 키를 풀어 쓴다. 새로 만들면 지난 실행에 잠근 값이 전부 풀리지 않는다. */
    @Test
    fun 앱을_다시_켜도_같은_데이터_키를_쓴다() {
        val kek = FakeKek()
        val store = MemoryDekStore()
        val sealed = assertNotNull(EnvelopeLocalSecretCipher(kek, store).seal("지난 실행".bytes()))
        val wrapped = store.stored?.copyOf()

        val reopened = EnvelopeLocalSecretCipher(kek, store)

        assertEquals("지난 실행", reopened.open(sealed)?.decodeToString())
        assertEquals(1, store.creates, "데이터 키를 다시 만들었다")
        assertContentEquals(wrapped, store.stored)
    }

    /**
     * 데이터 키를 풀지 못해도 **파일을 덮어쓰지 않는다.** 덮어쓰면 그 키로 잠근 값이 전부 영영 풀리지 않는다.
     * 그 실행 동안은 예전 방식으로 잠그고, 데이터 키로 잠근 값은 풀지 못한다고(null) 답한다 — 엉뚱한 값을 내지 않는다.
     */
    @Test
    fun 데이터_키를_풀지_못하면_파일을_지키고_예전_방식으로_동작한다() {
        val kek = FakeKek()
        val store = MemoryDekStore()
        val sealedBefore = assertNotNull(EnvelopeLocalSecretCipher(kek, store).seal("지난 실행".bytes()))
        val wrapped = store.stored?.copyOf()

        kek.failOpen = true
        val broken = EnvelopeLocalSecretCipher(kek, store)
        val sealedNow = assertNotNull(broken.seal("이번 실행".bytes()))

        assertContentEquals(wrapped, store.stored, "풀지 못한 데이터 키 파일을 덮어썼다")
        assertEquals(1, store.creates)
        assertFalse(sealedNow.startsWithMagic(), "쓸 수 없는 데이터 키로 잠갔다")
        assertNull(broken.open(sealedBefore), "풀 수 없는 값에 엉뚱한 값을 돌려줬다")

        kek.failOpen = false
        assertEquals("이번 실행", broken.open(sealedNow)?.decodeToString(), "예전 방식으로 잠근 값을 못 푼다")
    }

    /** 앱 시작 전(위치를 모를 때) 불려도 실패로 굳지 않는다 — 위치를 알게 되면 그때부터 데이터 키를 쓴다. */
    @Test
    fun 위치를_모를_때는_예전_방식으로_잠그고_알게_되면_데이터_키를_쓴다() {
        val store = MemoryDekStore(ready = false)
        val cipher = EnvelopeLocalSecretCipher(FakeKek(), store)

        val early = assertNotNull(cipher.seal("시작 전".bytes()))
        store.ready = true
        val later = assertNotNull(cipher.seal("시작 후".bytes()))

        assertFalse(early.startsWithMagic())
        assertTrue(later.startsWithMagic(), "앱 시작 전 한 번 실패로 굳어 계속 느린 방식을 쓴다")
        assertEquals("시작 전", cipher.open(early)?.decodeToString())
    }

    /** 예전 값은 앞 4바이트가 무작위 IV 라 우연히 표식과 같을 수 있다(2^-32). 그래도 풀려야 한다. */
    @Test
    fun 표식과_우연히_같게_시작하는_예전_값도_풀린다() {
        val magicIv = EnvelopeLocalSecretCipher.MAGIC + ByteArray(8) { 7 }
        val kek = FakeKek(ivOverride = magicIv)
        val legacy = kek.seal("우연".bytes())
        assertTrue(legacy.startsWithMagic())

        val cipher = EnvelopeLocalSecretCipher(FakeKek(), MemoryDekStore())

        assertEquals("우연", cipher.open(legacy)?.decodeToString())
    }

    @Test
    fun 엉터리_값은_풀지_않는다() {
        val cipher = EnvelopeLocalSecretCipher(FakeKek(), MemoryDekStore())
        val tampered = assertNotNull(cipher.seal("원문".bytes())).also { it[it.size - 1] = (it.last() + 1).toByte() }

        assertNull(cipher.open(tampered), "변조된 값을 풀었다")
        assertNull(cipher.open(ByteArray(40) { 9 }))
    }
}

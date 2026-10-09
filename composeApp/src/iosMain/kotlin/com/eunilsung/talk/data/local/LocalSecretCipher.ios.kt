package com.eunilsung.talk.data.local

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import korlibs.crypto.SecureRandom
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService
import platform.posix.size_tVar
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

actual fun platformLocalSecretCipher(): LocalSecretCipher =
    IosLocalSecretCipher { IosLocalSecretKey.material }

/**
 * iOS — AES-256-CBC 로 잠그고 HMAC-SHA256 으로 위·변조를 막는다(암호화한 뒤 서명).
 *
 * 애플이 Kotlin 에 열어 준 CommonCrypto 에는 GCM 이 없어 두 단계로 나눈다. 잠금과 검증에 서로 다른 키를 쓴다.
 * 저장 형식은 `[16바이트 IV][암호문][32바이트 서명]` 이다.
 *
 * 키를 [keyProvider] 로 받는 이유는 **테스트 실행 파일에 Keychain 이 없기 때문**이다(`errSecNotAvailable`).
 * 앱에서는 Keychain 키를, 테스트에서는 고정 키를 물려 **같은 암호 코드**를 검증한다.
 */
internal class IosLocalSecretCipher(
    private val keyProvider: () -> ByteArray?,
) : LocalSecretCipher {

    override fun seal(plain: ByteArray): ByteArray? {
        if (plain.isEmpty()) return null
        val material = keyProvider() ?: return null
        val iv = ByteArray(IV_SIZE).also { SecureRandom.nextBytes(it) }
        val cipherText = crypt(kCCEncrypt, plain, material.cipherKey(), iv) ?: return null
        return iv + cipherText + hmac(material.macKey(), iv + cipherText)
    }

    override fun open(payload: ByteArray): ByteArray? {
        if (payload.size <= IV_SIZE + MAC_SIZE) return null
        val material = keyProvider() ?: return null

        val iv = payload.copyOfRange(0, IV_SIZE)
        val cipherText = payload.copyOfRange(IV_SIZE, payload.size - MAC_SIZE)
        val mac = payload.copyOfRange(payload.size - MAC_SIZE, payload.size)
        if (!hmac(material.macKey(), iv + cipherText).matches(mac)) return null

        return crypt(kCCDecrypt, cipherText, material.cipherKey(), iv)
    }

    private fun ByteArray.cipherKey(): ByteArray = copyOfRange(0, CIPHER_KEY_SIZE)

    private fun ByteArray.macKey(): ByteArray = copyOfRange(CIPHER_KEY_SIZE, CIPHER_KEY_SIZE + MAC_KEY_SIZE)

    /** 서명 비교는 **끝까지** 본다 — 중간에 끊으면 걸린 자리로 값을 좁혀 갈 수 있다. */
    private fun ByteArray.matches(other: ByteArray): Boolean {
        if (size != other.size) return false
        var diff = 0
        for (i in indices) diff = diff or (this[i].toInt() xor other[i].toInt())
        return diff == 0
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun crypt(operation: UInt, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? = memScoped {
        if (data.isEmpty()) return null
        val out = ByteArray(data.size + IV_SIZE)
        val moved = alloc<size_tVar>()
        val status = data.usePinned { dataPin ->
            key.usePinned { keyPin ->
                iv.usePinned { ivPin ->
                    out.usePinned { outPin ->
                        CCCrypt(
                            operation, kCCAlgorithmAES, kCCOptionPKCS7Padding,
                            keyPin.addressOf(0), key.size.convert(), ivPin.addressOf(0),
                            dataPin.addressOf(0), data.size.convert(),
                            outPin.addressOf(0), out.size.convert(), moved.ptr,
                        )
                    }
                }
            }
        }
        if (status.toInt() != kCCSuccess.toInt()) return null
        out.copyOfRange(0, moved.value.toInt())
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArray(MAC_SIZE)
        key.usePinned { keyPin ->
            data.usePinned { dataPin ->
                out.usePinned { outPin ->
                    CCHmac(
                        kCCHmacAlgSHA256,
                        keyPin.addressOf(0), key.size.convert(),
                        dataPin.addressOf(0), data.size.convert(),
                        outPin.addressOf(0),
                    )
                }
            }
        }
        return out
    }

    internal companion object {
        const val IV_SIZE = 16
        const val MAC_SIZE = 32
        const val CIPHER_KEY_SIZE = 32
        const val MAC_KEY_SIZE = 32

        /** 잠금·서명 키를 합친 길이. 테스트가 고정 키를 만들 때도 쓴다. */
        const val KEY_SIZE = CIPHER_KEY_SIZE + MAC_KEY_SIZE
    }
}

/**
 * 키(64바이트)를 **Keychain** 에 둔다. 접근 등급은 `AfterFirstUnlockThisDeviceOnly` 다.
 *
 *  - 기기를 켠 뒤 한 번만 풀면 그 뒤로는 잠금 화면에서도 읽을 수 있다 — 화면이 잠긴 채로 푸시를 받아
 *    본문을 저장해야 하기 때문이다. 화면 잠금 기준(`WhenUnlocked`)으로 두면 그때 저장이 실패한다.
 *  - 백업이나 다른 기기로 따라가지 않는다. 그래서 백업을 복원해도 예전 본문은 풀리지 않고 서버에서 다시 받는다.
 */
@OptIn(
    ExperimentalForeignApi::class,
    ExperimentalEncodingApi::class,
    ExperimentalSettingsImplementation::class,
    ExperimentalSettingsApi::class,
)
private object IosLocalSecretKey {

    private const val ENTRY = "db_body_key_v1"
    private const val SERVICE = "com.eunilsung.talk.localsecret"

    private val key = RetryingKey(retryAfter = 1.seconds, load = ::loadOrCreate)

    /**
     * 키. 못 읽으면 null.
     *
     * 예전에는 `by lazy` 로 첫 결과를 프로세스 끝까지 들고 있었다. Keychain 을 한 번 못 읽으면(재부팅 뒤 첫 잠금 해제
     * 전에 푸시로 백그라운드 실행된 경우 등) 그 프로세스가 저장하는 대화 본문이 전부 풀 수 없는 값(`enc1:`)이 됐다.
     * 이제 성공만 기억하고 실패하면 다시 읽는다([RetryingKey]).
     *
     * 다시 읽어도 **기존 키를 덮어쓰지 않는다.** Keychain 이 잠겨 있으면 조회가 "없음" 이 아니라 오류를 던지므로
     * (multiplatform-settings 1.3.0 `checkError(errSecItemNotFound)`) 새 키를 만드는 분기로 가지 않는다.
     */
    val material: ByteArray? get() = key.get()

    private fun loadOrCreate(): ByteArray? = runCatching {
        val keychain = KeychainSettings(
            kSecAttrService to CFStringCreateWithCString(null, SERVICE, kCFStringEncodingUTF8),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )

        val stored = keychain.getStringOrNull(ENTRY)
        if (stored != null) {
            val bytes = runCatching { Base64.decode(stored) }.getOrNull()
            if (bytes != null && bytes.size == IosLocalSecretCipher.KEY_SIZE) return@runCatching bytes
        }

        val fresh = ByteArray(IosLocalSecretCipher.KEY_SIZE).also { SecureRandom.nextBytes(it) }
        keychain.putString(ENTRY, Base64.encode(fresh))
        fresh
    }.getOrNull()
}

/**
 * **성공한 값만 기억하는** 키 읽기. 실패하면 [retryAfter] 가 지난 뒤 다시 [load] 한다.
 *
 * 간격을 두는 이유: 키를 못 읽는 동안에도 대화 행마다 잠그기·풀기가 불리는데, 그때마다 Keychain 을 부르면 목록 하나에
 * 수백 번이 된다. 여러 스레드(메인·Default·소켓 수신)가 부르므로 잠근다.
 */
internal class RetryingKey(
    private val retryAfter: Duration,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val load: () -> ByteArray?,
) {
    private val lock = SynchronizedObject()
    private var loaded: ByteArray? = null
    private var failedAt: TimeMark? = null

    fun get(): ByteArray? = synchronized(lock) {
        loaded ?: run {
            val lastFailure = failedAt
            if (lastFailure != null && lastFailure.elapsedNow() < retryAfter) return@run null
            load().also { key -> if (key != null) loaded = key else failedAt = timeSource.markNow() }
        }
    }
}

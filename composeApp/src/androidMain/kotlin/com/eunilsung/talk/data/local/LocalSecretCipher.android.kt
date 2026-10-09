package com.eunilsung.talk.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual fun platformLocalSecretCipher(): LocalSecretCipher =
    EnvelopeLocalSecretCipher(kek = AndroidLocalSecretCipher, dekStore = FileDekStore { AndroidLocalSecretSetup.dekFile() })

/**
 * 잠긴 데이터 키를 둘 위치를 앱 시작 때 알려 준다([EnvelopeLocalSecretCipher]).
 *
 * **`Application.onCreate` 맨 앞에서 부른다** — 저장소들이 시작하자마자 잠그고 풀기 때문이다. 부르기 전에는 데이터 키를
 * 만들지 않고 예전 방식(칸마다 Keystore)으로 동작하므로, 늦게 불러도 값이 틀어지지는 않는다.
 *
 * 백업·기기 이전에서 빠지는 `noBackupFilesDir` 에 둔다. Keystore 키가 기기에 묶여 있어 파일만 옮겨 가도 풀 수 없다.
 */
object AndroidLocalSecretSetup {
    private const val DEK_FILE = "talk_local_dek_v1"

    @Volatile private var directory: File? = null

    fun init(context: Context) {
        directory = context.applicationContext.noBackupFilesDir
    }

    internal fun dekFile(): File? = directory?.let { File(it, DEK_FILE) }
}

/**
 * Android — AES-256-GCM. 키는 Keystore 안에서 만들어지고 **앱은 키 값을 볼 수 없다.**
 *
 * 지금은 칸을 직접 잠그지 않고 **데이터 키를 잠그는 데**와 이 방식으로 잠겼던 예전 값을 푸는 데만 쓴다
 * ([EnvelopeLocalSecretCipher]). 칸마다 부르면 보안 칩 IPC 때문에 느리다.
 *
 * 그래서 앱 데이터를 통째로 빼내도 키는 따라가지 않는다. 기기를 바꾸거나 앱을 지웠다 깔면 키가 사라져
 * 예전 본문을 풀 수 없는데, 서버에 원본이 있으므로 다시 받으면 된다.
 *
 * 저장 형식은 `[12바이트 IV][암호문+인증태그]` 다. GCM 이라 위·변조도 함께 걸러진다.
 */
internal object AndroidLocalSecretCipher : LocalSecretCipher {

    private const val ALIAS = "talk_local_body_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE_BITS = 256
    private const val IV_SIZE = 12
    private const val TAG_SIZE_BITS = 128

    private val key: SecretKey by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadOrCreateKey() }

    override fun seal(plain: ByteArray): ByteArray? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.iv + cipher.doFinal(plain)
    }.getOrNull()

    override fun open(payload: ByteArray): ByteArray? {
        if (payload.size <= IV_SIZE) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, payload, 0, IV_SIZE))
            cipher.doFinal(payload, IV_SIZE, payload.size - IV_SIZE)
        }.getOrNull()
    }

    private fun loadOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(KEY_SIZE_BITS)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    /** 잠금 화면이 없어도 푸시로 받은 본문을 저장해야 하므로 사용자 인증은 걸지 않는다. */
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }
}

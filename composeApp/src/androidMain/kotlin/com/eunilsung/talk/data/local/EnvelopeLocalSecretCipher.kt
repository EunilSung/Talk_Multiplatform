package com.eunilsung.talk.data.local

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android — **데이터 키로 잠그고, 데이터 키는 Keystore 로 잠근다.**
 *
 * 예전에는 칸 하나를 잠그고 풀 때마다 Keystore 키로 `Cipher.init` 을 불렀다. 그때마다 보안 칩(TEE)을 IPC 로 부르므로
 * 칸당 수 ms 가 들고, 여러 스레드가 동시에 부르면 줄을 선다. 그룹 사용자는 한 명에 잠긴 칸이 최대 13개라 목록 한 번에
 * 수백 번을 불렀고, 앱 시작 때 대화방·그룹 미리 풀기와 자동 로그인(저장된 아이디·비밀번호 풀기)이 같이 줄을 서
 * 로그인과 목록 표시가 함께 늦었다(2026-09-23 실기기 프로파일).
 *
 * 지금은 AES-256 데이터 키 하나를 [kek](Keystore 키)로 잠가 [dekStore] 에 두고, 프로세스에서 **한 번만** 풀어 메모리에
 * 둔다. 칸은 그 데이터 키로 소프트웨어 AES-GCM 을 돌려 µs 단위로 끝난다. Jetpack Security·Tink 와 같은 구조다.
 *
 * - **디스크에는 잠긴 데이터 키만 남는다.** Keystore 키는 기기 밖으로 나가지 않으므로, 앱 데이터를 통째로 빼내도
 *   데이터 키를 풀 수 없다. 백업·기기 이전에서도 빠진다(`noBackupFilesDir`, `dataExtractionRules`).
 * - **새 값은 [MAGIC] 으로 시작한다.** 없으면 예전 방식(Keystore 키로 직접 잠근 값)이라 [kek] 로 푼다. 예전 값의 앞
 *   4바이트는 무작위 IV 라 우연히 표식과 같을 수 있어(2^-32), 표식이 있어도 데이터 키로 못 풀면 예전 방식으로 한 번 더 본다.
 * - **데이터 키를 풀지 못하면 파일을 덮어쓰지 않는다.** 덮어쓰면 그 키로 잠근 값이 전부 영영 풀리지 않는다. 그 프로세스
 *   동안은 예전 방식으로 잠그고 풀며, 다음 실행에서 다시 시도한다.
 */
internal class EnvelopeLocalSecretCipher(
    private val kek: LocalSecretCipher,
    private val dekStore: DekStore,
) : LocalSecretCipher {

    private val random = SecureRandom()
    private val lock = Any()

    @Volatile private var dek: SecretKey? = null
    @Volatile private var dekFailed = false

    override fun seal(plain: ByteArray): ByteArray? {
        val key = dataKey() ?: return kek.seal(plain)
        return runCatching {
            val iv = ByteArray(IV_SIZE).also(random::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
            MAGIC + iv + cipher.doFinal(plain)
        }.getOrNull()
    }

    override fun open(payload: ByteArray): ByteArray? {
        if (!payload.hasMagic()) return kek.open(payload)
        val opened = dataKey()?.let { key ->
            runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, payload, MAGIC.size, IV_SIZE))
                val body = MAGIC.size + IV_SIZE
                cipher.doFinal(payload, body, payload.size - body)
            }.getOrNull()
        }
        return opened ?: kek.open(payload)
    }

    override fun isCurrent(payload: ByteArray): Boolean = payload.hasMagic()

    /**
     * 데이터 키. 처음 한 번만 풀거나 만든다.
     *
     * 저장소 위치를 아직 모르면(앱 시작 전) 실패로 굳히지 않고 다음에 다시 본다.
     */
    private fun dataKey(): SecretKey? {
        dek?.let { return it }
        if (dekFailed) return null
        synchronized(lock) {
            dek?.let { return it }
            if (dekFailed || !dekStore.isReady()) return null
            val loaded = loadOrCreate()
            if (loaded == null) dekFailed = true else dek = loaded
            return loaded
        }
    }

    private fun loadOrCreate(): SecretKey? {
        dekStore.read()?.let { wrapped -> return unwrap(wrapped) }

        val raw = ByteArray(DEK_SIZE).also(random::nextBytes)
        val wrapped = kek.seal(raw) ?: return null
        if (dekStore.create(wrapped)) return SecretKeySpec(raw, ALGORITHM)
        /** 그사이 다른 쪽이 먼저 만들었다 — 그 키를 쓴다. */
        return dekStore.read()?.let(::unwrap)
    }

    private fun unwrap(wrapped: ByteArray): SecretKey? =
        kek.open(wrapped)?.takeIf { it.size == DEK_SIZE }?.let { SecretKeySpec(it, ALGORITHM) }

    private fun ByteArray.hasMagic(): Boolean =
        size > MAGIC.size + IV_SIZE && MAGIC.indices.all { this[it] == MAGIC[it] }

    internal companion object {
        val MAGIC = byteArrayOf(0x54, 0x4C, 0x4B, 0x02)
        const val DEK_SIZE = 32
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_SIZE_BITS = 128
    }
}

/** 잠긴 데이터 키를 두는 곳. */
internal interface DekStore {
    /** 위치를 아는지. 모르면 데이터 키를 만들지도 읽지도 않는다. */
    fun isReady(): Boolean
    fun read(): ByteArray?

    /** **없을 때만** 쓴다. 이미 있으면 건드리지 않고 false. */
    fun create(bytes: ByteArray): Boolean
}

/** 앱 전용 파일. 임시 파일에 쓴 뒤 이름을 바꿔, 쓰다 끊겨도 반쪽짜리 키가 남지 않게 한다. */
internal class FileDekStore(private val fileProvider: () -> File?) : DekStore {

    override fun isReady(): Boolean = fileProvider() != null

    @Synchronized
    override fun read(): ByteArray? = runCatching {
        fileProvider()?.takeIf { it.isFile }?.readBytes()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    @Synchronized
    override fun create(bytes: ByteArray): Boolean = runCatching {
        val file = fileProvider() ?: return false
        if (file.exists()) return false
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(bytes)
        temp.renameTo(file)
    }.getOrDefault(false)
}

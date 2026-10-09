package com.eunilsung.talk.data.local

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 로컬 DB 에 넣는 대화 본문 잠그기 — 넣을 때 잠그고 꺼낼 때 푼다.
 *
 * 단말을 잃어버리거나 백업이 새어 나가도 대화 본문이 그대로 읽히지 않게 한다.
 * 키는 각 플랫폼의 보안 저장소(Android Keystore · iOS Keychain)에만 둔다([LocalSecretCipher]).
 *
 * ### 저장 형식
 *
 * `enc1:` + Base64(암호문). **접두어가 없으면 이 기능을 켜기 전에 저장된 평문이라 그대로 돌려준다** —
 * 그래서 DB 파일을 갈아엎거나 스키마를 올리지 않고도 켤 수 있다. 이미 쌓인 대화는 그대로 읽히고,
 * 새로 저장되는 것부터 잠긴다.
 *
 * ### 잠그지 못했을 때
 *
 * **절대 풀리지 않는 값([PREFIX] 만)을 저장한다.** 평문으로 두면 잠그는 의미가 없다. 서버가 없는 앱이라
 * 되살릴 원본이 없지만, 키를 쓸 수 없는 상황에서 평문을 남기는 것보다 빈 본문이 낫다.
 *
 * ### 풀지 못했을 때
 *
 * 앱을 지웠다 깔면 키와 DB 가 함께 지워진다. 백업 복원으로 DB 만 돌아오면 키가 없어 예전 본문을 풀 수 없는데,
 * 그래서 DB 파일을 백업에서 뺀다. [decryptOrNull] 이 null 을 돌려주고 [decrypt] 는 빈 문자열을 돌려준다.
 */
@OptIn(ExperimentalEncodingApi::class)
object LocalSecret {

    private const val PREFIX = "enc1:"

    /**
     * 실제로 잠그는 구현.
     *
     * **테스트에서만 바꿔 끼운다.** 플랫폼 보안 저장소는 테스트 실행 파일에서 쓸 수 없다 — Kotlin/Native 테스트에는
     * Keychain 이 없고(`errSecNotAvailable`), JVM 단위 테스트에는 Android Keystore 가 없다. 그래서 검증은 같은 암호
     * 코드에 고정 키를 물려 돌린다.
     */
    internal var cipher: LocalSecretCipher = platformLocalSecretCipher()
        set(value) {
            field = value
            /** 다른 키로 바꿨으면 앞 키로 푼 값은 더 이상 맞지 않는다. */
            openedCache.clear()
        }

    /**
     * 푼 값 캐시 — **같은 잠긴 값을 되풀이해 풀지 않는다.**
     *
     * 대화를 보낼 때마다 최신 30 행을 DB 에서 다시 읽어 행마다 본문·제목·보낸 사람 칸을 푼다. 잠긴 값은 잠글 때마다 IV 가
     * 달라 같은 문자열이면 같은 원문이고, 원문은 어차피 화면 메모리에 올라가는 값이라 캐시로 새로 드러나는 것은 없다.
     * 앱 프로세스 메모리에만 두고 저장하지 않는다.
     */
    private val openedCache = OpenedValueCache(maxEntries = 16_384, maxTotalChars = 4_000_000)

    /** 잠긴 값인지. 예전 평문과 구분하는 유일한 근거다. */
    fun isSealed(stored: String): Boolean = stored.startsWith(PREFIX)

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return plain
        val sealed = PREFIX + Base64.encode(cipher.seal(plain.encodeToByteArray()) ?: return PREFIX)
        /**
         * 방금 잠근 값은 곧바로 다시 읽힌다. 원문을 알고 있으니 풀지 않게 넣어 둔다. UTF-8 로 한 번 바꾼 값을 넣는다 —
         * 풀면 나오는 값과 같아야 하고, 짝이 깨진 글자(자른 이모지 반쪽 등)는 바꾸면 치환 문자가 된다.
         */
        openedCache.put(sealed, plain.encodeToByteArray().decodeToString())
        return sealed
    }

    /** 풀지 못하면 null. 잠기지 않은 예전 값은 그대로 돌려준다. */
    fun decryptOrNull(stored: String): String? {
        if (!isSealed(stored)) return stored
        openedCache.get(stored)?.let { return it }
        val payload = runCatching { Base64.decode(stored.substring(PREFIX.length)) }.getOrNull() ?: return null
        val opened = cipher.open(payload)?.decodeToString() ?: return null
        openedCache.put(stored, opened)
        return opened
    }

    /** 풀지 못하면 빈 문자열 — 없어도 화면이 도는 자리(목록 미리보기 등)에 쓴다. */
    fun decrypt(stored: String): String = decryptOrNull(stored) ?: ""

    /** 비어 있는 컬럼(null)은 null 로 둔다 — "값이 없다" 는 뜻을 잠그면서 잃지 않게 한다. */
    fun encryptNullable(plain: String?): String? = plain?.let(::encrypt)

    /** [encryptNullable] 로 넣은 값을 꺼낸다. null 은 null, 못 풀면 빈 문자열. */
    fun decryptNullable(stored: String?): String? = stored?.let(::decrypt)
}

/** 플랫폼 암호 — 키는 보안 저장소에만 두고 밖으로 꺼내지 않는다. 실패하면 던지지 않고 null 을 돌려준다. */
interface LocalSecretCipher {
    fun seal(plain: ByteArray): ByteArray?
    fun open(payload: ByteArray): ByteArray?

    /**
     * [payload] 가 **지금 [seal] 이 쓰는 방식**으로 잠긴 것인지. 잠그는 방식이 하나뿐인 구현(iOS·테스트)은 늘 true 다.
     * Android 는 칸마다 Keystore 를 부르던 방식과 데이터 키로 잠그는 방식을 가린다.
     */
    fun isCurrent(payload: ByteArray): Boolean = true
}

expect fun platformLocalSecretCipher(): LocalSecretCipher

/**
 * [LocalSecret] 의 푼 값 캐시. 여러 스레드(메인·Default)에서 부르므로 잠근다.
 *
 * 가득 차면 먼저 들어온 것부터 버린다 — 방을 옮겨 다니면 예전 방 값이 먼저 빠진다. 아주 긴 값은 넣지 않는다.
 */
private class OpenedValueCache(private val maxEntries: Int, private val maxTotalChars: Int) {
    private val lock = SynchronizedObject()
    private val entries = LinkedHashMap<String, String>()
    private var totalChars = 0

    fun get(sealed: String): String? = synchronized(lock) { entries[sealed] }

    fun put(sealed: String, opened: String) {
        val size = sealed.length + opened.length
        if (size > maxTotalChars / 16) return
        synchronized(lock) {
            if (entries.containsKey(sealed)) return
            entries[sealed] = opened
            totalChars += size
            while (entries.size > maxEntries || totalChars > maxTotalChars) {
                val oldest = entries.keys.first()
                totalChars -= oldest.length + (entries.remove(oldest)?.length ?: 0)
            }
        }
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        totalChars = 0
    }
}

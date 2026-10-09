package com.eunilsung.talk.testsupport

import com.eunilsung.talk.data.local.LocalSecret
import com.eunilsung.talk.data.local.LocalSecretCipher

/**
 * 테스트용 가짜 잠금.
 *
 * 앱은 키를 Keystore·Keychain 에 두는데 **테스트 실행 파일에는 둘 다 없다** — JVM 단위 테스트에는 Android Keystore 가 없고,
 * Kotlin/Native 테스트에는 Keychain 이 없다(`errSecNotAvailable`). 그대로 두면 잠그지 못해 풀 수 없는 값이 저장되고 저장소
 * 테스트가 본문을 읽지 못한다. 그래서 값을 뒤집기만 하는 가짜 암호를 끼운다. 저장소가 잠그고 푸는 길목을 지나는지만 보고,
 * 실제 암호는 플랫폼 테스트(`EnvelopeLocalSecretCipherTest`)가 본다.
 */
object TestLocalSecret {

    private class ReversingCipher : LocalSecretCipher {
        private var nonce = 0
        override fun seal(plain: ByteArray): ByteArray = byteArrayOf((nonce++).toByte()) + plain.reversedArray()
        override fun open(payload: ByteArray): ByteArray? =
            if (payload.size < 2) null else payload.copyOfRange(1, payload.size).reversedArray()
    }

    fun install() {
        LocalSecret.cipher = ReversingCipher()
    }
}

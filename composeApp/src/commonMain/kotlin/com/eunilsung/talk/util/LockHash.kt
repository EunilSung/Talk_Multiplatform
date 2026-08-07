package com.eunilsung.talk.util

import korlibs.crypto.SHA256
import korlibs.crypto.SecureRandom

/** 화면 잠금 비밀번호/패턴 해시 — salt + SHA-256. */
object LockHash {

    /** 랜덤 16바이트 salt (hex 문자열). 잠금 설정 시 1회 생성해 저장. */
    fun newSalt(): String = SecureRandom.nextBytes(ByteArray(16)).toHex()

    /** `salt:secret` 을 SHA-256 해시한 hex 문자열. */
    fun hash(secret: String, saltHex: String): String =
        SHA256.digest("$saltHex:$secret".encodeToByteArray()).hex

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

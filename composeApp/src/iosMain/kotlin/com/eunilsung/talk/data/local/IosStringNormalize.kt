package com.eunilsung.talk.data.local

import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.precomposedStringWithCanonicalMapping

/** 한글 파일명 NFD → NFC 정규화. */
internal fun String.toNfc(): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    return (this as NSString).precomposedStringWithCanonicalMapping
}

/** NFC → NFD(자모 분해) — iOS 파일시스템이 실제로 저장한 형태. */
internal fun String.toNfd(): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    return (this as NSString).decomposedStringWithCanonicalMapping
}

/** 실제 존재하는 파일 경로로 보정 — NFC 로 없으면 NFD 로 재시도. */
internal fun String.existingFilePathOrSelf(): String {
    val fm = NSFileManager.defaultManager
    if (fm.fileExistsAtPath(this)) return this
    val nfd = toNfd()
    return if (nfd != this && fm.fileExistsAtPath(nfd)) nfd else this
}

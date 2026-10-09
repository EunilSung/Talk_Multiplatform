package com.eunilsung.talk.data.local

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

/**
 * 경로를 iCloud·Finder 백업에서 뺀다. **디렉터리에 걸면 안의 파일 전부**가 빠진다.
 *
 * ### 왜 필요한가
 *
 * 본문은 Keychain 키로 잠겨 있고 그 키는 `ThisDeviceOnly` 라 백업에 따라가지 않는다. 그런데 DB 파일은
 * `Library/Application Support` 아래라 **백업에 들어간다.** 그래서 백업을 새 기기에 복원하면 DB 는
 * 돌아오고 키는 없어, 예전 대화가 빈 말풍선으로 남는다(대화 본문은 못 풀면 빈 문자열이 된다).
 * 파일 자체를 백업에서 빼면 그 상태에 들어가지 않는다. Android 는 `allowBackup=false` 로 같은 일을 한다.
 *
 * ### 디렉터리에 거는 이유
 *
 * SQLite 는 본 파일 옆에 `-wal`·`-shm`·`-journal` 을 만들고 지웠다 다시 만든다. 파일 하나에 건 속성은
 * 그 파일이 새로 만들어지면 사라지지만, 디렉터리에 건 속성은 남는다.
 *
 * @return 속성을 걸었으면 true. 경로가 없거나 실패하면 false — 앱은 계속 돈다(백업 제외는 보조 수단이다).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun excludeFromBackup(path: String): Boolean {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return false
    val url = NSURL.fileURLWithPath(path)
    return url.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
}

/** 백업에서 빠져 있는지. 확인·테스트용. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun isExcludedFromBackup(path: String): Boolean = memScoped {
    val url = NSURL.fileURLWithPath(path)
    val value = alloc<ObjCObjectVar<Any?>>()
    val ok = url.getResourceValue(value.ptr, forKey = NSURLIsExcludedFromBackupKey, error = null)
    ok && (value.value as? NSNumber)?.boolValue == true
}

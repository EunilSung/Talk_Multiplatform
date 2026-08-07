package com.eunilsung.talk.data.remote.share

/** Android — 복사한 파일이 없어 별도 cleanup 불필요. no-op. */
actual fun cleanupSharedFile(filePath: String) = Unit

actual fun cleanupOldSharedFiles(maxAgeMillis: Long) = Unit

package com.eunilsung.talk.data.remote.share

import kotlinx.cinterop.ExperimentalForeignApi
import com.eunilsung.talk.util.Log
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLContentModificationDateKey
import platform.Foundation.timeIntervalSince1970

private const val APP_GROUP_ID = "group.com.eunilsung.talk"
private const val SHARED_FILES_DIR_NAME = "SharedFiles"

/** 단일 파일 정리 — file:// URL 만 처리. */
@OptIn(ExperimentalForeignApi::class)
actual fun cleanupSharedFile(filePath: String) {
    if (!filePath.startsWith("file://")) return
    val url = NSURL.URLWithString(filePath) ?: return
    val ok = NSFileManager.defaultManager.removeItemAtURL(url, error = null)
    if (!ok) {
        Log.message("[Share/iOS] cleanupSharedFile: failed path=$filePath")
    }
}

/** App Group container 의 SharedFiles/ 안에서 [maxAgeMillis] 이상 된 파일을 일괄 삭제. */
@OptIn(ExperimentalForeignApi::class)
actual fun cleanupOldSharedFiles(maxAgeMillis: Long) {
    val fileManager = NSFileManager.defaultManager
    val containerURL = fileManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID)
        ?: run {
            Log.message("[Share/iOS] cleanupOldSharedFiles: container not available")
            return
        }
    val sharedDir = containerURL.URLByAppendingPathComponent(SHARED_FILES_DIR_NAME) ?: return

    val contents = fileManager.contentsOfDirectoryAtURL(
        url = sharedDir,
        includingPropertiesForKeys = listOf(NSURLContentModificationDateKey),
        options = 0u,
        error = null
    ) ?: return

    val nowSec = NSDate().timeIntervalSince1970
    val maxAgeSec = maxAgeMillis / 1000.0
    var removed = 0

    @Suppress("UNCHECKED_CAST")
    for (item in contents) {
        val fileURL = item as? NSURL ?: continue
        val path = fileURL.path ?: continue
        val attrs = fileManager.attributesOfItemAtPath(path, error = null) ?: continue
        val modDate = attrs["NSFileModificationDate"] as? NSDate ?: continue
        val ageSec = nowSec - modDate.timeIntervalSince1970
        if (ageSec > maxAgeSec) {
            val ok = fileManager.removeItemAtURL(fileURL, error = null)
            if (ok) removed++
        }
    }
    if (removed > 0) {
        Log.message("[Share/iOS] cleanupOldSharedFiles: removed=$removed (maxAge=${maxAgeMillis / 1000}s)")
    }
}

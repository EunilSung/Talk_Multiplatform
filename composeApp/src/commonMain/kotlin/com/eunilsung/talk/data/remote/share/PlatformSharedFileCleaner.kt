package com.eunilsung.talk.data.remote.share

import com.eunilsung.talk.domain.repository.SharedFileCleaner

/** 플랫폼별 정리 함수([cleanupSharedFile])에 잇는다. */
class PlatformSharedFileCleaner : SharedFileCleaner {
    override fun cleanup(filePath: String) = cleanupSharedFile(filePath)
}

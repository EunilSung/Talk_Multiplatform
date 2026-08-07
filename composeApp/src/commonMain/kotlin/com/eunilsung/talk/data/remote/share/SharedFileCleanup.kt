package com.eunilsung.talk.data.remote.share

/** 공유 진입 시 추출된 파일 하나 정리 (iOS: 삭제, Android: no-op). */
expect fun cleanupSharedFile(filePath: String)

/** 오래된 공유 파일 일괄 정리. @param maxAgeMillis 이 시간 이상 된 파일만 삭제. 기본 1시간. */
expect fun cleanupOldSharedFiles(maxAgeMillis: Long = 60 * 60 * 1000L)

package com.eunilsung.talk.data.local

/** 다운로드된 파일을 시스템 기본 앱으로 열기 — platform 별 abstraction. 성공 시 true. */
interface FileOpener {
    suspend fun open(path: String): Boolean
}

package com.eunilsung.talk.domain.repository

import com.eunilsung.talk.domain.model.VersionInfo

/** 서버 버전 정보와 현재 단말 버전 비교 → [VersionInfo]. 실패 시 null (버전체크 우회). */
interface VersionCheckRepository {
    suspend fun check(): VersionInfo?
}

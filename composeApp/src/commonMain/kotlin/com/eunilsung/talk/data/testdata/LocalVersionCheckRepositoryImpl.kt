package com.eunilsung.talk.data.testdata

import com.eunilsung.talk.domain.model.VersionInfo
import com.eunilsung.talk.domain.repository.VersionCheckRepository

/** 버전체크 로컬 대체 — 항상 "업데이트 불필요". */
class LocalVersionCheckRepositoryImpl : VersionCheckRepository {
    override suspend fun check(): VersionInfo = VersionInfo(
        currentVersion = 0,
        requiredVersion = 0,
        updateUrl = "",
    )
}

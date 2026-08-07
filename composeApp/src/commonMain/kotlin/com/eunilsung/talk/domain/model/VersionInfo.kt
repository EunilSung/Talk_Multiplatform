package com.eunilsung.talk.domain.model

/** 앱 버전 비교 결과 — [needsUpdate] 판정에 쓴다. */
data class VersionInfo(
    val currentVersion: Long,
    val requiredVersion: Long,
    val updateUrl: String,
) {
    val needsUpdate: Boolean get() = currentVersion < requiredVersion
}

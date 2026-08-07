package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.VersionInfo
import com.eunilsung.talk.domain.repository.VersionCheckRepository

/** 현재 단말 버전과 서버 버전을 비교 → [VersionInfo] (실패 시 null). */
class VersionCheckUseCase(private val repository: VersionCheckRepository) {
    suspend operator fun invoke(): VersionInfo? = repository.check()
}

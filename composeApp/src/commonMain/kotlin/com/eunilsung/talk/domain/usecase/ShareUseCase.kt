package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.repository.SharedFileCleaner

/** 공유로 넘겨받은 파일을 보낸 뒤 치운다. */
class CleanupSharedFileUseCase(private val cleaner: SharedFileCleaner) {
    operator fun invoke(filePath: String) = cleaner.cleanup(filePath)
}

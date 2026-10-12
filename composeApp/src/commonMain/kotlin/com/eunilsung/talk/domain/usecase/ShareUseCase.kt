package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.SharedContent
import com.eunilsung.talk.domain.repository.ChatRoomRepository
import com.eunilsung.talk.domain.repository.SharedFileCleaner

/**
 * 다른 앱이 공유로 넘겨준 글·파일을 대화방에 보낸다.
 *
 * 파일이 함께 오면 글은 보내지 않고 파일만 보낸다. 보낸 파일은 하나씩 치운다. 한 건이 실패해도 나머지는
 * 계속 보낸다.
 * @return 보내지 못한 것들의 실패 이유. 다 보냈으면 빈 목록.
 */
class SendSharedContentUseCase(
    private val chatRoomRepository: ChatRoomRepository,
    private val cleaner: SharedFileCleaner,
) {
    suspend operator fun invoke(chatRoomId: String, content: SharedContent): List<Throwable> {
        val failures = mutableListOf<Throwable>()
        if (content.hasText && !content.hasFiles) {
            runCatching { chatRoomRepository.sendTextChat(chatRoomId, content.text, null, null) }
                .onFailure { failures += it }
        }
        content.filePaths.forEach { path ->
            runCatching { chatRoomRepository.sendFile(chatRoomId, path) }.onFailure { failures += it }
            runCatching { cleaner.cleanup(path) }
        }
        return failures
    }
}

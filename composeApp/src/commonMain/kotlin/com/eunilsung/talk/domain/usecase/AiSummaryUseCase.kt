package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.AiSummaryRequest
import com.eunilsung.talk.domain.model.AiSummaryResult
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.repository.AiSummaryRepository
import com.eunilsung.talk.domain.repository.LoginRepository

/**
 * 파일 대화를 AI Agent 앱에서 요약하게 한다 — 서버 파일 id 와 로그인한 내 아이디·이름을 링크에 실어 그 앱을 연다.
 *
 * 서버 파일 id 는 방·대화로 먼저 찾는다. 같은 기기 파일을 여러 방에 보냈어도 이 방의 파일을 정확히 고르기 위해서다.
 * 그 기록이 없는 대화만 기기 경로로 찾고, 그마저 없으면(아직 받지 못한 파일) [AiSummaryResult.FILE_NOT_READY] 다.
 */
class RequestAiSummaryUseCase(
    private val repository: AiSummaryRepository,
    private val loginRepository: LoginRepository,
) {
    suspend operator fun invoke(chatRoomId: String, chat: Chat.Item): AiSummaryResult {
        val path = chat.localPath.ifBlank { chat.imagePath }
        val fileId = repository.findChatFileId(chatRoomId, chat.chatID)
            ?: path.takeIf { it.isNotBlank() }?.let(repository::findFileId)
            ?: return AiSummaryResult.FILE_NOT_READY
        val me = loginRepository.getMyUser()
        val request = AiSummaryRequest(
            fileId = fileId,
            fileName = chat.originalFileName.ifBlank { path.substringAfterLast('/') },
            userId = me.id,
            userName = me.name,
        )
        return if (repository.openSummary(request)) AiSummaryResult.OPENED else AiSummaryResult.APP_NOT_INSTALLED
    }
}

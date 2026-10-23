package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.local.AppLinkOpener
import com.eunilsung.talk.data.mapper.AiAgentLinkMapper
import com.eunilsung.talk.data.remote.server.ServerFileStore
import com.eunilsung.talk.domain.model.AiSummaryRequest
import com.eunilsung.talk.domain.repository.AiSummaryRepository

/**
 * 파일 대화를 AI Agent 앱에 넘긴다.
 *
 * 서버 파일 id 는 [ServerFileStore] 의 기록에서 찾는다 — 서버가 확정한 대화마다 적어 둔 "방·대화 → 파일 id" 가 먼저이고,
 * 그 기록이 없는 대화만 "파일 id → 기기 경로" 를 거꾸로 훑는다. 말풍선은 기기 경로만 들고 있다.
 * 앱을 여는 일은 플랫폼의 [AppLinkOpener] 가 한다.
 */
class AiSummaryRepositoryImpl(
    private val fileStore: ServerFileStore,
    private val linkMapper: AiAgentLinkMapper,
    private val opener: AppLinkOpener,
) : AiSummaryRepository {

    override fun findChatFileId(chatRoomId: String, chatId: String): String? = fileStore.chatFileId(chatRoomId, chatId)

    override fun findFileId(localPath: String): String? = fileStore.fileIdOf(localPath)

    override suspend fun openSummary(request: AiSummaryRequest): Boolean =
        opener.open(linkMapper.toSummaryLink(request))
}

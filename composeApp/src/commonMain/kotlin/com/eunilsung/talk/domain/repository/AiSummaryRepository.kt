package com.eunilsung.talk.domain.repository

import com.eunilsung.talk.domain.model.AiSummaryRequest

/** 파일 대화를 AI Agent 앱에 넘겨 요약하게 한다. */
interface AiSummaryRepository {

    /** 방 [chatRoomId] 의 파일 대화 [chatId] 가 담은 Talk 서버 파일 id. 서버가 확정한 그 대화를 받은 기록이 없으면 null. */
    fun findChatFileId(chatRoomId: String, chatId: String): String?

    /**
     * 기기 안 [localPath] 에 받아 둔 파일의 Talk 서버 파일 id. 아직 받지 않았으면 null.
     *
     * 같은 파일을 여러 방에 보냈으면 그중 아무 방의 id 일 수 있다. 대화 기록([findChatFileId])이 없을 때만 쓴다.
     */
    fun findFileId(localPath: String): String?

    /** AI Agent 앱을 요약 링크로 연다. 열었으면 true, 링크를 받을 앱이 없으면 false. */
    suspend fun openSummary(request: AiSummaryRequest): Boolean
}

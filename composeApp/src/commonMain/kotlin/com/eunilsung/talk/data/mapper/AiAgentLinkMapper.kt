package com.eunilsung.talk.data.mapper

import com.eunilsung.talk.domain.model.AiSummaryRequest
import io.ktor.http.encodeURLParameter

/**
 * 요약 요청 → AI Agent 앱 링크.
 *
 * `aiagent://summarize?fileId=<파일 id>&fileName=<파일 이름>&userId=<아이디>&userName=<이름>`
 *
 * 값은 모두 퍼센트 인코딩한다(공백은 `%20`, 한글은 UTF-8 바이트). 파일 이름과 사람 이름에 한글이 들어간다.
 * 받는 쪽(AI Agent)이 값을 검증하고, 맞지 않으면 링크를 무시한다.
 */
class AiAgentLinkMapper {

    fun toSummaryLink(request: AiSummaryRequest): String {
        val query = listOf(
            "fileId" to request.fileId,
            "fileName" to request.fileName,
            "userId" to request.userId,
            "userName" to request.userName,
        ).joinToString("&") { (key, value) -> "$key=${value.encodeURLParameter()}" }
        return "$SCHEME://$SUMMARIZE_HOST?$query"
    }

    companion object {
        const val SCHEME = "aiagent"
        const val SUMMARIZE_HOST = "summarize"
    }
}

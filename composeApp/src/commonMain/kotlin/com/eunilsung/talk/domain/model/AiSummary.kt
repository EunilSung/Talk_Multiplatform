package com.eunilsung.talk.domain.model

/**
 * AI Agent 앱에 넘기는 문서 요약 요청.
 *
 * @param fileId Talk 서버의 파일 id. AI Agent 는 이 id 로 Talk 서버에서 파일을 받는다.
 * @param fileName 말풍선에 보이는 파일 이름. AI Agent 가 질문 문구와 형식 판단에 쓴다.
 * @param userId 요청한 Talk 계정의 아이디. AI Agent 는 로그인한 계정과 같은지 확인한다.
 * @param userName 요청한 계정의 이름. 다른 계정으로 로그인돼 있을 때 안내 문구에 쓴다.
 */
data class AiSummaryRequest(
    val fileId: String,
    val fileName: String,
    val userId: String,
    val userName: String,
)

/** 파일 대화의 "AI 요약" 을 눌러 본 결과. */
enum class AiSummaryResult {
    /** AI Agent 앱을 열었다. 요약은 그 앱이 한다. */
    OPENED,

    /** 파일을 아직 기기에 받지 못해 서버 파일 id 를 모른다. 잠시 뒤 다시 하면 된다. */
    FILE_NOT_READY,

    /** 링크를 받을 AI Agent 앱이 기기에 없다. */
    APP_NOT_INSTALLED,
}

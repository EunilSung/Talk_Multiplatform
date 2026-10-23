package com.eunilsung.talk.data.mapper

import com.eunilsung.talk.domain.model.AiSummaryRequest
import kotlin.test.Test
import kotlin.test.assertEquals

/** AI Agent 요약 링크 — 정해 둔 모양 그대로, 값은 모두 퍼센트 인코딩한다. */
class AiAgentLinkMapperTest {

    private val mapper = AiAgentLinkMapper()

    @Test
    fun 영문_값은_그대로_정해진_순서로_싣는다() {
        val link = mapper.toSummaryLink(AiSummaryRequest("f_1-A", "report.pdf", "test1", "Kim"))

        assertEquals("aiagent://summarize?fileId=f_1-A&fileName=report.pdf&userId=test1&userName=Kim", link)
    }

    @Test
    fun 한글과_공백은_UTF8_퍼센트_인코딩하고_공백은_퍼센트20_이다() {
        val link = mapper.toSummaryLink(AiSummaryRequest("f1", "회의 록.hwp", "test1", "김민준"))

        assertEquals(
            "aiagent://summarize?fileId=f1" +
                "&fileName=%ED%9A%8C%EC%9D%98%20%EB%A1%9D.hwp" +
                "&userId=test1" +
                "&userName=%EA%B9%80%EB%AF%BC%EC%A4%80",
            link,
        )
    }

    @Test
    fun 쿼리를_깨뜨리는_기호도_인코딩한다() {
        val link = mapper.toSummaryLink(AiSummaryRequest("f1", "a&b=c?#+%.txt", "test1", ""))

        assertEquals("aiagent://summarize?fileId=f1&fileName=a%26b%3Dc%3F%23%2B%25.txt&userId=test1&userName=", link)
    }
}

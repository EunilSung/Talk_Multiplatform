package com.eunilsung.talk.data.remote.push

import com.eunilsung.talk.domain.model.PushPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 푸시 본문은 받은 그대로 두고, 이모티콘만 보낸 대화인지는 판정으로 알린다. 대체 문구는 알림을 띄우는 쪽이 기기 언어로 고른다. */
class PushPayloadParserTest {

    private val parser = PushPayloadParser()

    private fun parse(msg: String, msgType: String) = parser.parse(
        mapOf(
            PushPayloadParser.KEY_MSG to msg,
            PushPayloadParser.KEY_MSGTYPE to msgType,
            PushPayloadParser.KEY_MSG_KIND to "TALK",
        )
    )

    @Test
    fun 본문_없는_이모티콘은_본문을_비워_두고_판정만_한다() {
        val payload = parse(msg = "", msgType = PushPayload.CHAT_TYPE_EMOTICON)

        assertEquals("", payload.msg)
        assertTrue(payload.isEmoticonOnly)
    }

    @Test
    fun 글이_있는_이모티콘은_글을_그대로_쓴다() {
        val payload = parse(msg = "고마워요", msgType = PushPayload.CHAT_TYPE_EMOTICON)

        assertEquals("고마워요", payload.msg)
        assertFalse(payload.isEmoticonOnly)
    }

    @Test
    fun 일반_대화는_이모티콘이_아니다() {
        assertFalse(parse(msg = "", msgType = "1").isEmoticonOnly)
    }
}

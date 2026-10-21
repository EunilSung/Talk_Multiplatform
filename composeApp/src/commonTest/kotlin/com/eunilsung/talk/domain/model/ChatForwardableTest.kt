package com.eunilsung.talk.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 전달할 수 있는 대화 — 보내기가 끝났고 회수되지 않은 글·답장·이모티콘·첨부만. */
class ChatForwardableTest {

    private fun chat(type: String, content: String = "", status: String = Chat.Status.COMPLETE) =
        Chat.Item(chatID = "c1", chatType = type, chatContent = content, chatStatus = status)

    @Test
    fun 글_답장_이모티콘_첨부는_전달할_수_있다() {
        assertTrue(chat(Chat.Type.TEXT, "안녕").isForwardable)
        assertTrue(chat(Chat.Type.REPLY, "답장").isForwardable)
        assertTrue(chat(Chat.Type.EMOTICON).copy(emoticon = Emoticon(id = "(손말_오케이)")).isForwardable)
        assertTrue(chat(Chat.Type.IMAGE).copy(imagePath = "/cache/a.jpg").isForwardable)
        assertTrue(chat(Chat.Type.FILE, "12KB").copy(imagePath = "/cache/a.pdf").isForwardable)
    }

    @Test
    fun 보내는_중이거나_회수됐거나_빈_대화는_전달하지_않는다() {
        assertFalse(chat(Chat.Type.TEXT, "안녕", Chat.Status.SENDING).isForwardable)
        assertFalse(chat(Chat.Type.TEXT, "안녕", Chat.Status.FAIL).isForwardable)
        assertFalse(chat(Chat.Type.TEXT, "안녕").copy(isRecalled = true).isForwardable)
        assertFalse(chat(Chat.Type.TEXT, " ").isForwardable)
        assertFalse(chat(Chat.Type.IMAGE).isForwardable)
    }

    @Test
    fun 투표_공지_초대_같은_알림_대화는_전달하지_않는다() {
        assertFalse(chat(Chat.Type.VOTE, "투표").isForwardable)
        assertFalse(chat(Chat.Type.NOTICE, "공지").isForwardable)
        assertFalse(chat(Chat.Type.INVITE, "초대").isForwardable)
    }
}

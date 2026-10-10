package com.eunilsung.talk.ui.chatroom.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 입력창의 글을 통째로 바꾸고 되돌릴 때 멘션이 어떻게 되는지 본다. */
class MentionFieldStateTest {

    private fun stateWithMention(): MentionFieldState = MentionFieldState().apply {
        onValueChange(TextFieldValue("@", TextRange(1)))
        insertMention("test2", "이서연")
        val text = value.text + "자료 오늘까지 줘"
        onValueChange(TextFieldValue(text, TextRange(text.length)))
    }

    @Test
    fun 글을_바꿔도_이름이_남아_있는_멘션은_새_자리에서_이어진다() {
        val state = stateWithMention()

        state.replaceText("안녕하세요 @이서연 님, 자료를 오늘까지 보내주실 수 있을까요?")

        assertEquals(listOf(MentionSpan("test2", "이서연", 6, 10)), state.mentions)
        assertTrue("<mention>@이서연</mention>" in state.toTaggedText())
    }

    @Test
    fun 이름이_사라진_멘션은_보통_글자로_남는다() {
        val state = stateWithMention()

        state.replaceText("자료를 오늘까지 보내주세요")

        assertTrue(state.mentions.isEmpty())
        assertEquals("자료를 오늘까지 보내주세요", state.toTaggedText())
    }

    @Test
    fun 떠_둔_것으로_되돌리면_글과_멘션이_그대로_돌아온다() {
        val state = stateWithMention()
        val original = state.snapshot()
        val taggedBefore = state.toTaggedText()

        state.replaceText("전혀 다른 글")
        state.restore(original)

        assertEquals("@이서연 자료 오늘까지 줘", state.text)
        assertEquals(taggedBefore, state.toTaggedText())
    }
}

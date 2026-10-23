package com.eunilsung.talk.ui.chatroom

import com.eunilsung.talk.domain.model.Chat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 롱프레스 메뉴의 "AI 요약" — 서버 모드에서 보내기가 끝난 문서 파일 대화에만 뜬다. */
class AiSummaryMenuTest {

    private fun file(name: String, status: String = Chat.Status.COMPLETE) = Chat.Item(
        chatID = "c1",
        chatType = Chat.Type.FILE,
        chatStatus = status,
        originalFileName = name,
        imagePath = "/cache/$name",
    )

    private fun Chat.Item.shows(isServerMode: Boolean = true, isEnabled: Boolean = true) =
        canRequestAiSummary(isServerMode = isServerMode, isEnabled = isEnabled)

    @Test
    fun 요약할_수_있는_문서_파일에는_뜬다() {
        listOf("a.txt", "a.md", "a.csv", "a.pdf", "a.docx", "a.xlsx", "a.pptx", "a.hwp", "a.hwpx", "회의록.HWP")
            .forEach { assertTrue(file(it).shows(), "$it 에 뜨지 않는다") }
    }

    @Test
    fun 글을_꺼낼_수_없는_파일에는_뜨지_않는다() {
        listOf("a.doc", "a.xls", "a.ppt", "a.zip", "a.png", "a.mp4", "확장자없음")
            .forEach { assertFalse(file(it).shows(), "$it 에 떴다") }
    }

    @Test
    fun 로컬_모드이거나_기능을_끄면_뜨지_않는다() {
        assertFalse(file("a.pdf").shows(isServerMode = false))
        assertFalse(file("a.pdf").shows(isEnabled = false))
    }

    @Test
    fun 회수됐거나_보내는_중이거나_실패한_파일에는_뜨지_않는다() {
        assertFalse(file("a.pdf").copy(isRecalled = true).shows())
        assertFalse(file("a.pdf", Chat.Status.SENDING).shows())
        assertFalse(file("a.pdf", Chat.Status.FAIL).shows())
    }

    @Test
    fun 파일이_아닌_대화에는_뜨지_않는다() {
        assertFalse(file("a.pdf").copy(chatType = Chat.Type.TEXT).shows())
        assertFalse(file("a.pdf").copy(chatType = Chat.Type.IMAGE).shows())
        assertFalse(file("a.pdf").copy(chatType = Chat.Type.VIDEO).shows())
    }
}

package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.AiSummaryRequest
import com.eunilsung.talk.domain.model.AiSummaryResult
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.repository.AiSummaryRepository
import com.eunilsung.talk.testsupport.FakeLoginRepository
import com.eunilsung.talk.testsupport.TestMyInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AI 요약 맡기기 — 그 방 대화의 서버 파일 id(없으면 받아 둔 경로로 찾은 id)와 내 아이디·이름으로 AI Agent 를 열고,
 * 열지 못한 이유를 돌려준다.
 */
class RequestAiSummaryUseCaseTest {

    private class FakeAiSummary(
        private val chatFileIds: Map<Pair<String, String>, String> = emptyMap(),
        private val fileIds: Map<String, String> = emptyMap(),
        var isInstalled: Boolean = true,
    ) : AiSummaryRepository {
        val opened = mutableListOf<AiSummaryRequest>()

        override fun findChatFileId(chatRoomId: String, chatId: String): String? = chatFileIds[chatRoomId to chatId]

        override fun findFileId(localPath: String): String? = fileIds[localPath]

        override suspend fun openSummary(request: AiSummaryRequest): Boolean {
            opened += request
            return isInstalled
        }
    }

    private val roomId = "room-a"
    private val useCase get() = RequestAiSummaryUseCase(repository, FakeLoginRepository())
    private var repository = FakeAiSummary()

    private fun fileChat(path: String, name: String = "회의록.pdf", chatId: String = "c1") = Chat.Item(
        chatID = chatId,
        chatType = Chat.Type.FILE,
        chatStatus = Chat.Status.COMPLETE,
        originalFileName = name,
        imagePath = path,
        localPath = path,
    )

    @BeforeTest
    fun setUp() = TestMyInfo.loginAs("test1", "김민준")

    @AfterTest
    fun tearDown() = TestMyInfo.clear()

    @Test
    fun 받아_둔_파일은_서버_id_와_내_정보를_실어_연다() = runTest {
        repository = FakeAiSummary(fileIds = mapOf("/cache/abc_회의록.pdf" to "file-abc"))

        val result = useCase(roomId, fileChat("/cache/abc_회의록.pdf"))

        assertEquals(AiSummaryResult.OPENED, result)
        assertEquals(listOf(AiSummaryRequest("file-abc", "회의록.pdf", "test1", "김민준")), repository.opened)
    }

    @Test
    fun 경로만_있는_대화는_localPath_가_비면_imagePath_로_찾는다() = runTest {
        repository = FakeAiSummary(fileIds = mapOf("/cache/a.txt" to "file-a"))

        val result = useCase(roomId, fileChat("").copy(imagePath = "/cache/a.txt"))

        assertEquals(AiSummaryResult.OPENED, result)
        assertEquals("file-a", repository.opened.single().fileId)
    }

    @Test
    fun 파일_이름이_비면_경로의_마지막_이름을_쓴다() = runTest {
        repository = FakeAiSummary(fileIds = mapOf("/cache/abc_보고서.docx" to "file-abc"))

        useCase(roomId, fileChat("/cache/abc_보고서.docx", name = ""))

        assertEquals("abc_보고서.docx", repository.opened.single().fileName)
    }

    @Test
    fun 아직_받지_않은_파일은_열지_않고_받는_중이라고_한다() = runTest {
        repository = FakeAiSummary()

        assertEquals(AiSummaryResult.FILE_NOT_READY, useCase(roomId, fileChat("")))
        assertEquals(AiSummaryResult.FILE_NOT_READY, useCase(roomId, fileChat("/cache/unknown.pdf")))
        assertEquals(emptyList(), repository.opened)
    }

    @Test
    fun 받을_앱이_없으면_설치되지_않았다고_한다() = runTest {
        repository = FakeAiSummary(fileIds = mapOf("/cache/a.pdf" to "file-a"), isInstalled = false)

        assertEquals(AiSummaryResult.APP_NOT_INSTALLED, useCase(roomId, fileChat("/cache/a.pdf")))
    }

    @Test
    fun 그_방_대화의_기록이_있으면_경로로_찾은_id_보다_먼저_쓴다() = runTest {
        repository = FakeAiSummary(
            chatFileIds = mapOf((roomId to "c1") to "file-this-room"),
            fileIds = mapOf("/device/a.pdf" to "file-other-room"),
        )

        useCase(roomId, fileChat("/device/a.pdf"))

        assertEquals("file-this-room", repository.opened.single().fileId)
    }

    @Test
    fun 그_방_대화의_기록이_있으면_아직_받지_않은_파일도_연다() = runTest {
        repository = FakeAiSummary(chatFileIds = mapOf((roomId to "c1") to "file-a"))

        assertEquals(AiSummaryResult.OPENED, useCase(roomId, fileChat("")))
        assertEquals("file-a", repository.opened.single().fileId)
    }

    @Test
    fun 다른_방의_같은_대화_id_기록은_쓰지_않고_경로로_찾는다() = runTest {
        repository = FakeAiSummary(
            chatFileIds = mapOf(("room-b" to "c1") to "file-room-b"),
            fileIds = mapOf("/device/a.pdf" to "file-by-path"),
        )

        useCase(roomId, fileChat("/device/a.pdf"))

        assertEquals("file-by-path", repository.opened.single().fileId)
    }
}

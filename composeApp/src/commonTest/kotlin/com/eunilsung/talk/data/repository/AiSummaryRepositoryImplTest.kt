package com.eunilsung.talk.data.repository

import com.eunilsung.talk.data.mapper.AiAgentLinkMapper
import com.eunilsung.talk.data.remote.server.ServerFileStore
import com.eunilsung.talk.domain.model.AiSummaryRequest
import com.eunilsung.talk.testsupport.FakeAppLinkOpener
import com.eunilsung.talk.testsupport.FakeFileMetadataResolver
import com.eunilsung.talk.testsupport.FakeTalkServer
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AI 요약 저장소 — 서버 파일 id 를 방·대화의 기록과 기기 경로에서 찾고, 링크를 만들어 AI Agent 를 연다. */
class AiSummaryRepositoryImplTest {

    private val store = ServerFileStore(MapSettings(), FakeTalkServer(), FakeFileMetadataResolver())
    private val opener = FakeAppLinkOpener()
    private val repository = AiSummaryRepositoryImpl(store, AiAgentLinkMapper(), opener)

    @Test
    fun 같은_경로를_두_방에_보낸_뒤에도_대화로_찾으면_그_방의_파일_id_다() {
        store.remember("file-x", "/device/a.pdf")
        store.rememberChatFile("room-a", "chat-a", "file-x")
        store.remember("file-y", "/device/a.pdf")
        store.rememberChatFile("room-b", "chat-b", "file-y")

        assertEquals("file-x", repository.findFileId("/device/a.pdf"), "경로로만 찾으면 먼저 적힌 방(회수·나간 방일 수 있다)의 id 다")
        assertEquals("file-y", repository.findChatFileId("room-b", "chat-b"))
        assertEquals("file-x", repository.findChatFileId("room-a", "chat-a"))
    }

    @Test
    fun 기록이_없는_대화는_null_이다() {
        store.rememberChatFile("room-a", "chat-a", "file-x")

        assertNull(repository.findChatFileId("room-a", "chat-other"))
        assertNull(repository.findChatFileId("room-b", "chat-a"))
    }

    @Test
    fun 요약_링크를_만들어_열고_받을_앱이_없으면_false_다() = runTest {
        val request = AiSummaryRequest("file-y", "a.pdf", "test1", "Kim")

        assertTrue(repository.openSummary(request))
        opener.isInstalled = false
        assertFalse(repository.openSummary(request))

        assertEquals(List(2) { AiAgentLinkMapper().toSummaryLink(request) }, opener.requested)
    }
}

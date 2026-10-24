package com.eunilsung.talk.ui.chatroom.linkpreview

import com.eunilsung.talk.domain.model.LinkPreview
import com.eunilsung.talk.domain.model.LinkPreviewState
import com.eunilsung.talk.domain.repository.LinkPreviewRepository
import com.eunilsung.talk.domain.usecase.GetLinkPreviewUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 링크 미리보기 카드의 상태 — 받은 대화의 링크는 누르기 전까지 기기가 받으러 가지 않는다.
 *
 * 저절로 받으면 보낸 사람이 받는 사람의 IP·열람 시각을 알고, 사내망 주소로 요청을 보내게 할 수 있다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkPreviewViewModelTest {

    private class FakeLinkPreviews : LinkPreviewRepository {
        val requested = mutableListOf<String>()

        override suspend fun getState(url: String): StateFlow<LinkPreviewState> {
            requested += url
            return MutableStateFlow(LinkPreviewState.Success(LinkPreview(url = url, title = "제목")))
        }
    }

    private val previews = FakeLinkPreviews()
    private lateinit var viewModel: LinkPreviewViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = LinkPreviewViewModel(GetLinkPreviewUseCase(previews))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun 받은_대화의_링크는_누르기_전까지_받지_않는다() = runTest {
        viewModel.onAction(LinkPreviewActions.OnShown(URL, isMe = false))

        assertEquals(emptyList(), previews.requested)
        assertTrue(URL in viewModel.uiState.value.waitingUrls)
    }

    @Test
    fun 미리보기_보기를_누르면_그때_받는다() = runTest {
        viewModel.onAction(LinkPreviewActions.OnShown(URL, isMe = false))
        viewModel.onAction(LinkPreviewActions.Load(URL))

        assertEquals(listOf(URL), previews.requested)
        assertTrue(URL !in viewModel.uiState.value.waitingUrls)
        assertIs<LinkPreviewState.Success>(viewModel.uiState.value.previews[URL])
    }

    @Test
    fun 내가_보낸_대화의_링크는_바로_받는다() = runTest {
        viewModel.onAction(LinkPreviewActions.OnShown(URL, isMe = true))

        assertEquals(listOf(URL), previews.requested)
        assertIs<LinkPreviewState.Success>(viewModel.uiState.value.previews[URL])
    }

    @Test
    fun 같은_주소는_한_번만_받는다() = runTest {
        viewModel.onAction(LinkPreviewActions.OnShown(URL, isMe = true))
        viewModel.onAction(LinkPreviewActions.OnShown(URL, isMe = false))
        viewModel.onAction(LinkPreviewActions.Load(URL))

        assertEquals(listOf(URL), previews.requested)
        assertTrue(URL !in viewModel.uiState.value.waitingUrls)
    }

    @Test
    fun 받으면_안_되는_주소는_묻지_않고_카드를_숨긴다() = runTest {
        listOf("http://example.com/a", "https://192.168.0.1/admin", "https://localhost:8090/").forEach { blocked ->
            viewModel.onAction(LinkPreviewActions.OnShown(blocked, isMe = true))
            viewModel.onAction(LinkPreviewActions.Load(blocked))

            assertEquals(LinkPreviewState.Failure, viewModel.uiState.value.previews[blocked], blocked)
            assertTrue(blocked !in viewModel.uiState.value.waitingUrls, blocked)
        }
        assertEquals(emptyList(), previews.requested)
    }

    private companion object {
        const val URL = "https://www.example.com/page"
    }
}

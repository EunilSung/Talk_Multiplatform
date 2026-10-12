package com.eunilsung.talk.functest

import com.eunilsung.talk.domain.model.VoteSummary
import com.eunilsung.talk.shared.api.CreateVoteRequest
import com.eunilsung.talk.ui.chatroom.vote.VoteActions
import com.eunilsung.talk.ui.chatroom.vote.VoteEvent
import com.eunilsung.talk.ui.chatroom.vote.VoteMode
import com.eunilsung.talk.ui.chatroom.vote.VoteUiState
import com.eunilsung.talk.ui.chatroom.vote.VoteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 투표 화면의 상태 — 목록·만들기·참여·결과를 오가며 진짜 서버의 집계와 버튼 판정이 맞게 보이는지.
 *
 * 서버의 집계 자체는 [FileAndVoteScenarioTest] 가 본다. 여기서는 화면이 어느 단계에 있고 무엇을 누를 수 있는지 본다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoteViewModelScenarioTest {

    /** ViewModel 은 메인 스레드에서 돈다. 테스트에는 메인 스레드가 없어 그 대역([viewModelMainThread])을 세운다. */
    @BeforeTest
    fun setUp() = Dispatchers.setMain(viewModelMainThread)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun 투표화면() = functionalSuite("투표화면") { harness ->
        fun VoteViewModel.list(): VoteUiState.List? = uiState.value as? VoteUiState.List
        fun VoteViewModel.create(): VoteUiState.Create =
            uiState.value as? VoteUiState.Create ?: throw StepFailure("만들기 화면이 아니다: ${uiState.value}")
        fun VoteViewModel.participate(): VoteUiState.Participate =
            uiState.value as? VoteUiState.Participate ?: throw StepFailure("참여 화면이 아니다: ${uiState.value}")
        fun VoteViewModel.result(): VoteUiState.Result =
            uiState.value as? VoteUiState.Result ?: throw StepFailure("결과 화면이 아니다: ${uiState.value}")

        listOf(
            scenario("만들고참여하고마감") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val viewModel = harness.voteViewModel()
                var summary = VoteSummary()

                step("처음 열면 빈 목록이 보인다") {
                    harness.chats.fetchChats(roomId)
                    viewModel.onAction(VoteActions.Load(VoteMode.LIST, chatRoomId = roomId))
                    awaitTrue("목록이 뜬다") { viewModel.list() != null }
                    requireEquals(emptyList<VoteSummary>(), viewModel.list()?.votes, "처음 목록")
                }
                step("제목과 두 항목이 차지 않으면 보내지 않고 항목은 두 개 아래로 줄지 않는다") {
                    viewModel.onAction(VoteActions.OnCreateClick)
                    viewModel.onAction(VoteActions.OnTitleChange("$tag 점심"))
                    viewModel.onAction(VoteActions.OnItemContentChange(0, "짜장"))
                    viewModel.onAction(VoteActions.OnSubmitCreate)
                    require(!viewModel.create().isSubmitting, "모자란 폼을 보냈다")
                    viewModel.onAction(VoteActions.OnRemoveItem(1))
                    requireEquals(2, viewModel.create().form.items.size, "남은 항목 수")
                }
                step("항목을 채워 만들면 목록으로 돌아오고 내가 만든 투표로 보인다") {
                    viewModel.onAction(VoteActions.OnAddItem)
                    viewModel.onAction(VoteActions.OnItemContentChange(1, "짬뽕"))
                    viewModel.onAction(VoteActions.OnItemContentChange(2, "볶음밥"))
                    viewModel.onAction(VoteActions.OnSubmitCreate)
                    awaitTrue("목록에 새 투표") { viewModel.list()?.votes?.any { it.title == "$tag 점심" } == true }
                    summary = viewModel.list()!!.votes.first { it.title == "$tag 점심" }
                    require(summary.isMine && !summary.hasVoted && !summary.isClosed, "목록의 표시가 다르다: $summary")
                    require(peer.client.votes(roomId).must("상대 투표 목록").any { it.id == summary.id }, "상대 목록에 없다")
                }
                step("목록에서 고르면 참여 화면이 뜨고 하나만 고를 수 있다") {
                    viewModel.onAction(VoteActions.OnVoteClick(summary))
                    awaitTrue("참여 화면") { viewModel.uiState.value is VoteUiState.Participate }
                    val options = viewModel.participate().detail.options
                    requireEquals(listOf("짜장", "짬뽕", "볶음밥"), options.map { it.content }, "보이는 항목")
                    viewModel.onAction(VoteActions.OnToggleOption(options[0].idx))
                    viewModel.onAction(VoteActions.OnToggleOption(options[1].idx))
                    requireEquals(setOf(options[1].idx), viewModel.participate().selected, "고른 항목")
                }
                step("던지면 상대가 먼저 던진 표와 함께 결과가 보이고 다시 투표·마감을 할 수 있다") {
                    val first = viewModel.participate().detail.options.first()
                    peer.client.castVote(roomId, summary.id, listOf(first.idx)).must("상대 투표")
                    viewModel.onAction(VoteActions.OnSubmitVote)
                    awaitTrue("결과 화면") { viewModel.uiState.value is VoteUiState.Result }
                    val result = viewModel.result().result
                    requireEquals(
                        mapOf("짜장" to 1, "짬뽕" to 1, "볶음밥" to 0),
                        result.items.associate { it.content to it.nVote },
                        "항목별 득표",
                    )
                    require(result.canReVote && result.canClose, "결과 화면의 버튼 판정이 다르다: $result")
                }
                step("다시 투표하면 내 표가 빠진 참여 화면으로 돌아온다") {
                    viewModel.onAction(VoteActions.OnReVote)
                    awaitTrue("참여 화면") { viewModel.uiState.value is VoteUiState.Participate }
                    require(!viewModel.participate().detail.hasVoted, "다시 투표했는데 참여한 것으로 나온다")
                    val voters = peer.client.vote(roomId, summary.id).must("상대가 본 투표").voters.map { it.userId }
                    requireEquals(listOf(RealServerHarness.PEER), voters, "서버에 남은 표")
                }
                step("다시 던지고 마감하면 목록에 마감으로 보이고 결과만 열린다") {
                    viewModel.onAction(VoteActions.OnToggleOption(viewModel.participate().detail.options[2].idx))
                    viewModel.onAction(VoteActions.OnSubmitVote)
                    awaitTrue("결과 화면") { viewModel.uiState.value is VoteUiState.Result }
                    viewModel.onAction(VoteActions.OnCloseVote)
                    awaitTrue("목록에 마감으로") {
                        viewModel.list()?.votes?.firstOrNull { it.id == summary.id }?.isClosed == true
                    }
                    viewModel.onAction(VoteActions.OnVoteClick(viewModel.list()!!.votes.first { it.id == summary.id }))
                    awaitTrue("결과 화면") { viewModel.uiState.value is VoteUiState.Result }
                    val result = viewModel.result().result
                    require(result.isClosed && !result.canReVote && !result.canClose, "마감한 투표의 버튼 판정이 다르다: $result")
                }
                step("뒤로 가면 목록을 거쳐 화면이 닫힌다") {
                    viewModel.onAction(VoteActions.OnBack)
                    require(viewModel.list() != null, "목록으로 돌아오지 않았다: ${viewModel.uiState.value}")
                    viewModel.onAction(VoteActions.OnBack)
                    awaitEvent<VoteEvent.Close>(viewModel.events, "닫기 알림")
                }
            },
            scenario("상대가만든투표") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val viewModel = harness.voteViewModel()
                var voteId = ""

                step("투표 대화에서 바로 참여 화면을 열면 여러 개를 고를 수 있다") {
                    harness.chats.fetchChats(roomId)
                    val request = CreateVoteRequest("$tag 회의", listOf("오전", "오후"), multiSelect = true)
                    voteId = peer.client.createVote(roomId, request).must("상대 투표 만들기").vote.id
                    viewModel.onAction(VoteActions.Load(VoteMode.PARTICIPATE, voteId, roomId))
                    awaitTrue("참여 화면") { viewModel.uiState.value is VoteUiState.Participate }
                    val options = viewModel.participate().detail.options
                    options.forEach { viewModel.onAction(VoteActions.OnToggleOption(it.idx)) }
                    requireEquals(options.map { it.idx }.toSet(), viewModel.participate().selected, "고른 항목")
                }
                step("던진 결과에서 다시 투표는 되고 남의 투표라 마감은 되지 않는다") {
                    viewModel.onAction(VoteActions.OnSubmitVote)
                    awaitTrue("결과 화면") { viewModel.uiState.value is VoteUiState.Result }
                    val result = viewModel.result().result
                    requireEquals(2, result.totalVotes, "던진 표")
                    require(result.canReVote && !result.canClose, "결과 화면의 버튼 판정이 다르다: $result")
                    viewModel.onAction(VoteActions.OnCloseVote)
                    require(!peer.client.vote(roomId, voteId).must("상대가 본 투표").isClosed, "남의 투표가 마감됐다")
                }
            },
        )
    }
}

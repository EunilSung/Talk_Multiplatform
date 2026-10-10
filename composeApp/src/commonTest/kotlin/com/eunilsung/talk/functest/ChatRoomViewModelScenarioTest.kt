package com.eunilsung.talk.functest

import com.eunilsung.talk.data.remote.push.CurrentChatRoomTracker
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.GroupedChat
import com.eunilsung.talk.domain.model.PolishStyle
import com.eunilsung.talk.ui.chatroom.ChatRoomActions
import com.eunilsung.talk.ui.chatroom.ChatRoomUiState
import com.eunilsung.talk.ui.chatroom.ChatRoomViewModel
import com.eunilsung.talk.ui.chatroom.ChatTranslationUiState
import com.eunilsung.talk.ui.chatroom.PolishUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 대화방 화면의 상태 — ViewModel 이 진짜 서버의 응답과 알림으로 화면 상태를 맞게 만드는지.
 *
 * 화면이 하는 일(방 열기, 보내기, 길게 눌러 번역, 다듬기)을 액션으로 넣고, 화면이 그릴 상태를 본다.
 * 버튼을 실제로 누르지는 않는다 — 버튼과 액션의 연결은 여기서 확인하지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomViewModelScenarioTest {

    /** ViewModel 은 메인 스레드에서 돈다. 테스트에는 메인 스레드가 없어 다른 스레드를 대신 세운다. */
    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        CurrentChatRoomTracker.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun 대화방화면() = functionalSuite("대화방화면") { harness ->
        fun ChatRoomViewModel.shown(): List<GroupedChat> =
            (uiState.value as? ChatRoomUiState.Success)?.groupedChats.orEmpty()

        listOf(
            scenario("안읽음표시선과읽음") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val viewModel = harness.chatRoomViewModel()

                step("방을 열면 안 읽은 첫 대화 위에만 표시선이 선다") {
                    val read = peer.say(roomId, "$tag 읽은 말")
                    harness.chats.fetchChats(roomId)
                    harness.chats.markChatAsRead(roomId, read.id)
                    repeat(UNREAD) { peer.say(roomId, "$tag 새 말 ${it + 1}") }
                    awaitTrue("안읽음이 쌓인다") { harness.room(roomId)?.unReadCount == UNREAD.toString() }

                    CurrentChatRoomTracker.set(roomId)
                    viewModel.onAction(ChatRoomActions.Load(roomId))
                    awaitTrue("표시선이 선다") { viewModel.shown().any { it.showUnreadMarker } }
                    val marked = viewModel.shown().filter { it.showUnreadMarker }.map { it.chat.chatContent }
                    requireEquals(listOf("$tag 새 말 1"), marked, "표시선이 선 대화")
                }
                step("화면에 띄운 방은 읽은 것으로 처리돼 서버의 안읽음이 0 이 된다") {
                    awaitTrue("서버의 안읽음이 0") { harness.server.room(roomId).must("방 조회").unreadCount == 0 }
                }
            },
            scenario("보내기와공감") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val viewModel = harness.chatRoomViewModel()
                CurrentChatRoomTracker.set(roomId)
                viewModel.onAction(ChatRoomActions.Load(roomId))

                step("보내면 화면 목록에 전송 완료로 나타나고 상대에게 간다") {
                    viewModel.onAction(ChatRoomActions.OnSendText("$tag 화면에서 보냄"))
                    awaitTrue("화면에 전송 완료") {
                        viewModel.shown().any { it.chat.chatContent == "$tag 화면에서 보냄" && it.chat.chatStatue == Chat.Statue.COMPLETE }
                    }
                    require(peer.messages(roomId).any { it.content == "$tag 화면에서 보냄" }, "상대에게 가지 않았다")
                }
                step("상대의 대화가 열려 있는 화면에 들어오고 공감을 누르면 붙는다") {
                    val theirs = peer.say(roomId, "$tag 상대의 말")
                    awaitTrue("화면에 도착") { viewModel.shown().any { it.chat.chatID == theirs.id } }
                    viewModel.onAction(ChatRoomActions.OnSendEmpathy(theirs.id, EMPATHY))
                    awaitTrue("화면에 공감 반영") {
                        viewModel.shown().first { it.chat.chatID == theirs.id }.chat.empathy.empathy2.any { it.id == RealServerHarness.ME }
                    }
                }
                step("회수하면 화면의 그 대화가 회수된 것으로 바뀐다") {
                    val mine = viewModel.shown().first { it.chat.chatContent == "$tag 화면에서 보냄" }.chat
                    viewModel.onAction(ChatRoomActions.OnRecallChat(mine.chatID))
                    awaitTrue("화면에 회수 반영") { viewModel.shown().first { it.chat.chatID == mine.chatID }.chat.isRecalled }
                }
            },
            scenario("번역상태") {
                val peer = harness.peer(RealServerHarness.PEER)
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val message = peer.say(roomId, "Please review the document by Friday")
                val viewModel = harness.chatRoomViewModel()
                CurrentChatRoomTracker.set(roomId)
                viewModel.onAction(ChatRoomActions.Load(roomId))
                awaitTrue("대화가 화면에 뜬다") { viewModel.shown().any { it.chat.chatID == message.id } }

                step("번역을 고르면 그 말풍선에 번역이 붙는다") {
                    viewModel.onAction(ChatRoomActions.OnTranslateChat(message.id, "ko"))
                    require(viewModel.translations.value.containsKey(message.id), "번역을 청한 직후에 진행 표시가 없다")
                    awaitTrue("번역이 끝난다", AI_TIMEOUT_MS) { viewModel.translations.value[message.id] !is ChatTranslationUiState.Loading }
                    val state = viewModel.translations.value[message.id]
                        ?: skip("서버의 AI 가 꺼져 있거나 한도에 걸려 번역이 걷혔다")
                    val text = (state as ChatTranslationUiState.Ready).text
                    require(text.isNotBlank() && text != message.content, "번역이 비었거나 원문과 같다: $text")
                    requireEquals(setOf(message.id), viewModel.translations.value.keys, "번역이 붙은 말풍선")
                }
                step("번역 숨기기를 고르면 걷힌다") {
                    viewModel.onAction(ChatRoomActions.OnHideTranslation(message.id))
                    require(viewModel.translations.value.isEmpty(), "숨긴 번역이 남아 있다")
                }
                step("다른 방을 열면 앞 방의 번역이 따라오지 않는다") {
                    viewModel.onAction(ChatRoomActions.OnTranslateChat(message.id, "ko"))
                    val other = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                    viewModel.onAction(ChatRoomActions.Load(other))
                    require(viewModel.translations.value.isEmpty(), "방을 바꿨는데 번역이 남아 있다")
                }
            },
            scenario("다듬기상태") {
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val viewModel = harness.chatRoomViewModel()
                viewModel.onAction(ChatRoomActions.Load(roomId))
                val draft = "내일 회의 몇시에 하는지 알려주세여"

                step("다듬기를 고르면 받는 중이 되었다가 원문과 함께 결과가 준비된다") {
                    viewModel.onAction(ChatRoomActions.OnPolishText(draft, PolishStyle.CORRECT))
                    requireEquals(PolishUiState.Loading, viewModel.polish.value, "청한 직후의 상태")
                    awaitTrue("다듬기가 끝난다", AI_TIMEOUT_MS) { viewModel.polish.value != PolishUiState.Loading }
                    val state = viewModel.polish.value
                    if (state == PolishUiState.Failed) skip("서버의 AI 가 꺼져 있거나 한도에 걸렸다")
                    val ready = state as PolishUiState.Ready
                    requireEquals(draft, ready.sourceText, "결과가 가리키는 원문")
                    require(ready.text.isNotBlank(), "다듬은 글이 비어 있다")
                }
                step("화면이 결과를 받아 가면 상태가 비워진다") {
                    viewModel.onAction(ChatRoomActions.OnPolishHandled)
                    requireEquals(PolishUiState.Idle, viewModel.polish.value, "받아 간 뒤의 상태")
                }
                step("빈 글은 서버에 묻지 않는다") {
                    viewModel.onAction(ChatRoomActions.OnPolishText("   ", PolishStyle.CORRECT))
                    requireEquals(PolishUiState.Idle, viewModel.polish.value, "빈 글을 넣은 뒤의 상태")
                }
            },
        )
    }

    private companion object {
        const val UNREAD = 3
        const val EMPATHY = "2"
        const val AI_TIMEOUT_MS = 40_000L
    }
}

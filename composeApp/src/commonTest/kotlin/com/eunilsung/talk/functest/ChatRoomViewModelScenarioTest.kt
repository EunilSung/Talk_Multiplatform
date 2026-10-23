package com.eunilsung.talk.functest

import com.eunilsung.talk.data.remote.push.CurrentChatRoomTracker
import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.ui.chatroom.GroupedChat
import com.eunilsung.talk.domain.model.PolishStyle
import com.eunilsung.talk.ui.chatroom.ChatRoomActions
import com.eunilsung.talk.ui.chatroom.ChatRoomEvent
import com.eunilsung.talk.ui.chatroom.canRequestAiSummary
import com.eunilsung.talk.ui.chatroom.ChatMessagesUiState
import com.eunilsung.talk.ui.chatroom.ChatRoomViewModel
import com.eunilsung.talk.ui.chatroom.ChatTranslationUiState
import com.eunilsung.talk.ui.chatroom.PolishUiState
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.SendMessageRequest
import io.ktor.http.encodeURLParameter
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

    /** ViewModel 은 메인 스레드에서 돈다. 테스트에는 메인 스레드가 없어 그 대역([viewModelMainThread])을 세운다. */
    @BeforeTest
    fun setUp() = Dispatchers.setMain(viewModelMainThread)

    @AfterTest
    fun tearDown() {
        CurrentChatRoomTracker.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun 대화방화면() = functionalSuite("대화방화면") { harness ->
        fun ChatRoomViewModel.shown(): List<GroupedChat> =
            (uiState.value.messages as? ChatMessagesUiState.Success)?.groupedChats.orEmpty()

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
                        viewModel.shown().any { it.chat.chatContent == "$tag 화면에서 보냄" && it.chat.chatStatus == Chat.Status.COMPLETE }
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
                    require(viewModel.uiState.value.translations.containsKey(message.id), "번역을 청한 직후에 진행 표시가 없다")
                    awaitTrue("번역이 끝난다", AI_TIMEOUT_MS) { viewModel.uiState.value.translations[message.id] !is ChatTranslationUiState.Loading }
                    val state = viewModel.uiState.value.translations[message.id]
                        ?: skip("서버의 AI 가 꺼져 있거나 한도에 걸려 번역이 걷혔다")
                    val text = (state as ChatTranslationUiState.Ready).text
                    require(text.isNotBlank() && text != message.content, "번역이 비었거나 원문과 같다: $text")
                    requireEquals(setOf(message.id), viewModel.uiState.value.translations.keys, "번역이 붙은 말풍선")
                }
                step("번역 숨기기를 고르면 걷힌다") {
                    viewModel.onAction(ChatRoomActions.OnHideTranslation(message.id))
                    require(viewModel.uiState.value.translations.isEmpty(), "숨긴 번역이 남아 있다")
                }
                step("다른 방을 열면 앞 방의 번역이 따라오지 않는다") {
                    viewModel.onAction(ChatRoomActions.OnTranslateChat(message.id, "ko"))
                    val other = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                    viewModel.onAction(ChatRoomActions.Load(other))
                    require(viewModel.uiState.value.translations.isEmpty(), "방을 바꿨는데 번역이 남아 있다")
                }
            },
            scenario("다듬기상태") {
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val viewModel = harness.chatRoomViewModel()
                viewModel.onAction(ChatRoomActions.Load(roomId))
                val draft = "내일 회의 몇시에 하는지 알려주세여"

                step("다듬기를 고르면 받는 중이 되었다가 원문과 함께 결과가 준비된다") {
                    viewModel.onAction(ChatRoomActions.OnPolishText(draft, PolishStyle.CORRECT))
                    requireEquals(PolishUiState.Loading, viewModel.uiState.value.polish, "청한 직후의 상태")
                    awaitTrue("다듬기가 끝난다", AI_TIMEOUT_MS) { viewModel.uiState.value.polish != PolishUiState.Loading }
                    val state = viewModel.uiState.value.polish
                    if (state == PolishUiState.Failed) skip("서버의 AI 가 꺼져 있거나 한도에 걸렸다")
                    val ready = state as PolishUiState.Ready
                    requireEquals(draft, ready.sourceText, "결과가 가리키는 원문")
                    require(ready.text.isNotBlank(), "다듬은 글이 비어 있다")
                }
                step("화면이 결과를 받아 가면 상태가 비워진다") {
                    viewModel.onAction(ChatRoomActions.OnPolishHandled)
                    requireEquals(PolishUiState.Idle, viewModel.uiState.value.polish, "받아 간 뒤의 상태")
                }
                step("빈 글은 서버에 묻지 않는다") {
                    viewModel.onAction(ChatRoomActions.OnPolishText("   ", PolishStyle.CORRECT))
                    requireEquals(PolishUiState.Idle, viewModel.uiState.value.polish, "빈 글을 넣은 뒤의 상태")
                }
            },
            scenario("AI요약") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val theirName = "$tag 회의록.txt"
                val theirs = peer.client.uploadFile(roomId, theirName, "$tag 회의록 본문".encodeToByteArray()).must("상대 올리기")
                peer.client.sendMessage(
                    roomId,
                    SendMessageRequest(
                        "ft-file-${nowMillis()}", "", MessageKind.FILE,
                        MessagePayloadDto(fileId = theirs.id, fileName = theirs.name, fileSize = theirs.size),
                    ),
                ).must("상대 파일 대화")
                val viewModel = harness.chatRoomViewModel()
                CurrentChatRoomTracker.set(roomId)
                viewModel.onAction(ChatRoomActions.Load(roomId))
                fun shownFile(name: String): Chat.Item? =
                    viewModel.shown().firstOrNull { it.chat.originalFileName == name }?.chat
                fun linkHas(fileId: String) {
                    val link = harness.appLinks.requested.last()
                    require(link.startsWith("aiagent://summarize?"), "AI Agent 요약 링크가 아니다: $link")
                    require("fileId=${fileId.encodeURLParameter()}&" in link, "링크의 파일 id 가 서버 id($fileId)가 아니다: $link")
                    require("userId=${RealServerHarness.ME}&" in link, "링크에 내 아이디가 없다: $link")
                }

                step("받아 둔 상대의 문서는 서버 파일 id 와 내 아이디를 실은 링크로 AI Agent 를 연다") {
                    awaitTrue("파일을 기기에 받는다") { shownFile(theirName)?.imagePath?.isNotBlank() == true }
                    val chat = shownFile(theirName)!!
                    require(chat.canRequestAiSummary(isServerMode = true), "AI 요약 메뉴가 뜨지 않는 대화다")
                    harness.appLinks.isInstalled = true
                    harness.appLinks.requested.clear()
                    viewModel.onAction(ChatRoomActions.OnRequestAiSummary(chat))
                    awaitTrue("링크를 연다") { harness.appLinks.requested.isNotEmpty() }
                    linkHas(theirs.id)
                }
                step("AI Agent 가 없으면 설치 안내를 알린다") {
                    harness.appLinks.isInstalled = false
                    viewModel.onAction(ChatRoomActions.OnRequestAiSummary(shownFile(theirName)!!))
                    awaitEvent<ChatRoomEvent.AiAgentNotInstalled>(viewModel.events, "설치 안내")
                }
                step("기기에 받기 전이라도 서버에서 받은 대화는 그 대화의 서버 파일 id 로 연다") {
                    val notYet = shownFile(theirName)!!.copy(imagePath = "", localPath = "")
                    harness.appLinks.isInstalled = true
                    harness.appLinks.requested.clear()
                    viewModel.onAction(ChatRoomActions.OnRequestAiSummary(notYet))
                    awaitTrue("링크를 연다") { harness.appLinks.requested.isNotEmpty() }
                    linkHas(theirs.id)
                }
                step("서버 파일 id 를 알 길이 없는 대화(기록도 받아 둔 파일도 없음)는 받는 중이라고 알린다") {
                    val unknown = shownFile(theirName)!!.copy(chatID = "$tag-unknown", imagePath = "", localPath = "")
                    viewModel.onAction(ChatRoomActions.OnRequestAiSummary(unknown))
                    awaitEvent<ChatRoomEvent.AiSummaryFileNotReady>(viewModel.events, "받는 중 안내")
                }
                step("내가 보낸 문서도 서버 파일 id 로 링크를 만든다") {
                    val myName = "$tag-mine.md"
                    harness.deviceFiles.put("/device/$myName", "# $tag".encodeToByteArray())
                    viewModel.onAction(ChatRoomActions.OnSendFiles(listOf("/device/$myName")))
                    awaitTrue("파일 전송 완료") { shownFile(myName)?.chatStatus == Chat.Status.COMPLETE }
                    val serverId = peer.messages(roomId).first { it.payload?.fileName == myName }.payload?.fileId.orEmpty()
                    harness.appLinks.isInstalled = true
                    harness.appLinks.requested.clear()
                    viewModel.onAction(ChatRoomActions.OnRequestAiSummary(shownFile(myName)!!))
                    awaitTrue("링크를 연다") { harness.appLinks.requested.isNotEmpty() }
                    linkHas(serverId)
                }
            },
            scenario("AI요약같은파일여러방") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomA = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val roomB = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val roomC = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val name = "$tag-shared.txt"
                val path = "/device/$name"
                harness.deviceFiles.put(path, "$tag shared".encodeToByteArray())
                val viewModel = harness.chatRoomViewModel()
                suspend fun sentIn(roomId: String): Chat.Item? =
                    harness.chatsOf(roomId).firstOrNull { it.originalFileName == name && it.chatStatus == Chat.Status.COMPLETE }
                suspend fun serverFileIdIn(roomId: String): String =
                    peer.messages(roomId).first { it.payload?.fileName == name }.payload?.fileId.orEmpty()
                suspend fun summaryLink(roomId: String, chat: Chat.Item): String {
                    CurrentChatRoomTracker.set(roomId)
                    viewModel.onAction(ChatRoomActions.Load(roomId))
                    harness.appLinks.isInstalled = true
                    harness.appLinks.requested.clear()
                    viewModel.onAction(ChatRoomActions.OnRequestAiSummary(chat))
                    awaitTrue("링크를 연다") { harness.appLinks.requested.isNotEmpty() }
                    return harness.appLinks.requested.last()
                }
                fun linkFileId(link: String): String = link.substringAfter("fileId=").substringBefore('&')
                lateinit var chatB: Chat.Item
                lateinit var chatC: Chat.Item
                lateinit var idA: String
                lateinit var idB: String
                lateinit var idC: String

                step("같은 기기 파일을 두 방에 보내고 한 방의 것을 다른 방에 전달하면 서버 파일 id 가 방마다 다르다") {
                    listOf(roomA, roomB, roomC).forEach { harness.chats.fetchChats(it) }
                    harness.chats.sendFile(roomA, path)
                    harness.chats.sendFile(roomB, path)
                    val chatA = sentIn(roomA) ?: throw StepFailure("A 방에 보낸 파일이 완료되지 않았다")
                    chatB = sentIn(roomB) ?: throw StepFailure("B 방에 보낸 파일이 완료되지 않았다")
                    harness.chats.forwardChat(roomC, roomA, chatA)
                    chatC = sentIn(roomC) ?: throw StepFailure("C 방에 전달한 파일이 완료되지 않았다")
                    idA = serverFileIdIn(roomA)
                    idB = serverFileIdIn(roomB)
                    idC = serverFileIdIn(roomC)
                    requireEquals(3, setOf(idA, idB, idC).filter { it.isNotBlank() }.size, "방마다 다른 서버 파일 id 수")
                    require(listOf(chatA, chatB, chatC).all { it.localPath.ifBlank { it.imagePath } == path }, "세 말풍선이 같은 기기 파일을 가리키지 않는다")
                }
                step("A 방의 대화를 회수해 그 파일이 서버에서 지워져도 B 방의 요약은 B 방의 파일 id 로 연다") {
                    harness.chats.recallChat(roomA, sentIn(roomA)?.chatID ?: throw StepFailure("A 방 대화가 없다"))
                    require(harness.server.downloadFile(idA) !is ServerResult.Success, "회수한 A 방 파일을 아직 받을 수 있다")
                    val link = summaryLink(roomB, chatB)
                    requireEquals(idB, linkFileId(link), "B 방 요약 링크의 파일 id")
                    harness.server.downloadFile(idB).must("내 토큰으로 B 방 파일 받기(AI Agent 가 하는 일)")
                }
                step("B 방을 나가 그 파일을 받을 수 없어도 C 방(전달한 방)의 요약은 C 방의 파일 id 로 연다") {
                    harness.server.leaveRoom(roomB).must("B 방 나가기")
                    require(harness.server.downloadFile(idB) !is ServerResult.Success, "나간 B 방의 파일을 아직 받을 수 있다")
                    val link = summaryLink(roomC, chatC)
                    requireEquals(idC, linkFileId(link), "C 방 요약 링크의 파일 id")
                    harness.server.downloadFile(idC).must("내 토큰으로 C 방 파일 받기(AI Agent 가 하는 일)")
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

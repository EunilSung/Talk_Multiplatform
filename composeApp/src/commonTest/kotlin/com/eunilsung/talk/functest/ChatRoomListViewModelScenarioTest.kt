package com.eunilsung.talk.functest

import com.eunilsung.talk.domain.model.ALARM_OFF
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.util.ChatRoomNameValidation
import com.eunilsung.talk.ui.chatroomlist.ChatRoomListActions
import com.eunilsung.talk.ui.chatroomlist.ChatRoomListEvent
import com.eunilsung.talk.ui.chatroomlist.ChatRoomListKind
import com.eunilsung.talk.ui.chatroomlist.ChatRoomListMode
import com.eunilsung.talk.ui.chatroomlist.ChatRoomListViewModel
import com.eunilsung.talk.ui.chatroomlist.group.ChatRoomGroupManageActions
import com.eunilsung.talk.ui.uikit.search.Search
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 대화함 화면의 상태 — 검색어·필터 칩·편집 모드·대화그룹 칩이 진짜 서버의 방과 알림 위에서 맞게 만들어지는지.
 *
 * 저장소에 반영되는 것은 [ChatRoomListScenarioTest] 가 본다. 여기서는 화면이 그 위에서 무엇을 보이고 무엇을 알리는지
 * 본다. 버튼을 실제로 누르지는 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomListViewModelScenarioTest {

    /** ViewModel 은 메인 스레드에서 돈다. 테스트에는 메인 스레드가 없어 그 대역([viewModelMainThread])을 세운다. */
    @BeforeTest
    fun setUp() = Dispatchers.setMain(viewModelMainThread)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun 대화함화면() = functionalSuite("대화함화면") { harness ->
        fun ChatRoomListViewModel.shownIds(): List<String> = uiState.value.chatItems.map { it.id }
        fun ChatRoomListViewModel.shown(roomId: String): ChatRoom.Item =
            uiState.value.chatItems.firstOrNull { it.id == roomId } ?: throw StepFailure("화면에 방이 없다: $roomId")
        suspend fun serverRoomIds(): List<String> = harness.server.rooms().must("방 목록").map { it.id }

        listOf(
            scenario("이름과검색과안읽음칩") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val first = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val second = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val viewModel = harness.chatRoomListViewModel()

                step("새로 만든 방이 목록에 보인다") {
                    awaitTrue("두 방이 뜬다") { viewModel.shownIds().containsAll(listOf(first, second)) }
                }
                step("이름을 바꾸면 결과가 알려지고 목록의 이름이 바뀐다") {
                    viewModel.onAction(ChatRoomListActions.RenameChatRoom(first, "  $tag 가  "))
                    val renamed = awaitEvent<ChatRoomListEvent.ChatRoomRenamed>(viewModel.events, "이름 바꾸기 결과")
                    requireEquals(ChatRoomListEvent.ChatRoomRenamed(true, "$tag 가"), renamed, "알린 결과")
                    awaitTrue("목록의 이름이 바뀐다") { viewModel.shown(first).title == "$tag 가" }
                }
                step("빈 이름은 서버에 묻지 않고 거절된다") {
                    viewModel.onAction(ChatRoomListActions.RenameChatRoom(first, "   "))
                    val invalid = awaitEvent<ChatRoomListEvent.RenameInvalid>(viewModel.events, "빈 이름 거절")
                    requireEquals(ChatRoomNameValidation.Empty, invalid.reason, "거절 사유")
                    requireEquals("$tag 가", harness.server.room(first).must("방 조회").title, "서버의 방 이름")
                }
                step("검색어를 넣으면 이름이 맞는 방만 남는다") {
                    require(harness.rooms.renameChatRoom(second, "$tag 나"), "둘째 방 이름 바꾸기가 실패했다")
                    viewModel.onAction(ChatRoomListActions.OnSearchQueryChange(tag))
                    awaitTrue("두 방만 남는다") { viewModel.shownIds().toSet() == setOf(first, second) }
                    requireEquals(ChatRoomListKind.SEARCH, viewModel.uiState.value.kind, "목록 종류")
                    viewModel.onAction(ChatRoomListActions.OnSearchQueryChange("$tag 나"))
                    awaitTrue("둘째 방만 남는다") { viewModel.shownIds() == listOf(second) }
                }
                step("검색어를 지우면 모든 방이 다시 보인다") {
                    viewModel.onAction(ChatRoomListActions.OnClearSearch)
                    awaitTrue("두 방이 다시 보인다") { viewModel.shownIds().containsAll(listOf(first, second)) }
                    requireEquals(ChatRoomListKind.IDLE, viewModel.uiState.value.kind, "목록 종류")
                }
                step("안읽음 칩에는 상대가 말을 건 방만 들어오고 배지 합계가 따라간다") {
                    viewModel.onAction(ChatRoomListActions.OnFilterTypeChange(Search.FilterType.UNREAD))
                    peer.say(second, "$tag 안 읽은 말")
                    awaitTrue("말을 건 방이 들어온다") { second in viewModel.shownIds() }
                    requireEquals(ChatRoomListKind.UNREAD, viewModel.uiState.value.kind, "목록 종류")
                    require(first !in viewModel.shownIds(), "말이 없는 방이 안읽음 칩에 있다")
                    require(viewModel.uiState.value.chatItems.all { it.hasUnread }, "안읽음 칩에 다 읽은 방이 있다")
                    awaitTrue("배지 합계가 저장소와 같다") {
                        viewModel.uiState.value.unreadTotal == harness.rooms.unreadTotal.value
                    }
                    require(viewModel.uiState.value.unreadTotal >= 1, "배지 합계에 이 방의 몫이 없다")
                }
                step("전체 칩으로 돌아가면 다 읽은 방도 보인다") {
                    viewModel.onAction(ChatRoomListActions.OnFilterTypeChange(Search.FilterType.ALL))
                    awaitTrue("두 방이 다시 보인다") { viewModel.shownIds().containsAll(listOf(first, second)) }
                }
            },
            scenario("고정과알림과편집모드나가기") {
                val tag = runTag()
                val kept = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val left = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val viewModel = harness.chatRoomListViewModel()

                step("이름 붙인 두 방이 목록에 보인다") {
                    require(harness.rooms.renameChatRoom(kept, "$tag 남길 방"), "이름 바꾸기가 실패했다")
                    require(harness.rooms.renameChatRoom(left, "$tag 나갈 방"), "이름 바꾸기가 실패했다")
                    awaitTrue("두 방이 뜬다") { viewModel.shownIds().containsAll(listOf(kept, left)) }
                }
                step("고정하면 결과가 알려지고 그 방이 맨 위로 온다") {
                    viewModel.onAction(ChatRoomListActions.SetChatRoomPin(kept, true))
                    val pinned = awaitEvent<ChatRoomListEvent.ChatRoomPinChanged>(viewModel.events, "고정 결과")
                    requireEquals(ChatRoomListEvent.ChatRoomPinChanged(success = true, pinned = true), pinned, "알린 결과")
                    awaitTrue("맨 위로 온다") { viewModel.shownIds().firstOrNull() == kept }
                }
                step("알림을 끄면 결과가 알려지고 알림 끔으로 보인다") {
                    viewModel.onAction(ChatRoomListActions.SetChatRoomAlarm(kept, ALARM_OFF))
                    val muted = awaitEvent<ChatRoomListEvent.ChatRoomAlarmChanged>(viewModel.events, "알림 끄기 결과")
                    requireEquals(ChatRoomListEvent.ChatRoomAlarmChanged(success = true, muted = true), muted, "알린 결과")
                    awaitTrue("알림 끔으로 보인다") { viewModel.shown(kept).isMuted }
                }
                step("편집 모드에서 고른 방 중 화면에 보이는 방만 나간다") {
                    viewModel.onAction(ChatRoomListActions.SetMode(ChatRoomListMode.EDIT))
                    viewModel.onAction(ChatRoomListActions.OnSelectRoom(viewModel.shown(kept)))
                    viewModel.onAction(ChatRoomListActions.OnSelectRoom(viewModel.shown(left)))
                    requireEquals(setOf(kept, left), viewModel.uiState.value.selectedRoomIds, "고른 방")
                    viewModel.onAction(ChatRoomListActions.OnSearchQueryChange("$tag 나갈"))
                    awaitTrue("나갈 방만 보인다") { viewModel.shownIds() == listOf(left) }

                    viewModel.onAction(ChatRoomListActions.LeaveSelectedChatRooms)
                    val result = awaitEvent<ChatRoomListEvent.ChatRoomsBulkLeft>(viewModel.events, "나가기 결과")
                    requireEquals(ChatRoomListEvent.ChatRoomsBulkLeft(left = 1, failed = 0), result, "알린 결과")
                    val mine = serverRoomIds()
                    require(left !in mine, "고른 방이 서버에 남아 있다")
                    require(kept in mine, "화면에 보이지 않던 방까지 나갔다")
                    require(!viewModel.uiState.value.isEditMode, "편집 모드가 끝나지 않았다")
                    requireEquals(emptySet<String>(), viewModel.uiState.value.selectedRoomIds, "나간 뒤의 선택")
                }
                step("하나를 나가면 결과가 알려지고 목록에서 사라진다") {
                    viewModel.onAction(ChatRoomListActions.OnClearSearch)
                    viewModel.onAction(ChatRoomListActions.LeaveChatRoom(kept))
                    val result = awaitEvent<ChatRoomListEvent.ChatRoomLeft>(viewModel.events, "나가기 결과")
                    requireEquals(ChatRoomListEvent.ChatRoomLeft(success = true), result, "알린 결과")
                    awaitTrue("목록에서 사라진다") { kept !in viewModel.shownIds() }
                    require(kept !in serverRoomIds(), "나간 방이 서버에 남아 있다")
                }
            },
            scenario("대화그룹칩") {
                val peer = harness.peer(RealServerHarness.PEER)
                val tag = runTag()
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val list = harness.chatRoomListViewModel()
                val manage = harness.chatRoomGroupManageViewModel()
                var groupId = ""

                /** 관리 화면은 보는 사람이 있을 때만 그룹을 받는다. 물을 때마다 잠깐 본다. */
                suspend fun managed() = manage.uiState.first().groups

                step("관리 화면에서 그룹을 만들면 관리 화면과 대화함의 칩에 나타난다") {
                    manage.onAction(ChatRoomGroupManageActions.CreateGroup(tag))
                    awaitTrue("관리 화면에 나타난다") { managed().any { it.name == tag } }
                    groupId = managed().first { it.name == tag }.id
                    awaitTrue("대화함 칩에 나타난다") { list.uiState.value.chatGroups.any { it.id == groupId } }
                }
                step("방을 그룹에 담으면 결과가 알려지고 그 칩을 고르면 그 방만 보인다") {
                    list.onAction(ChatRoomListActions.OnAddRoomToGroup(roomId, groupId))
                    val added = awaitEvent<ChatRoomListEvent.ChatRoomAddedToGroup>(list.events, "담기 결과")
                    requireEquals(tag, added.groupName, "알린 그룹 이름")
                    list.onAction(ChatRoomListActions.OnGroupFilterChange(groupId))
                    awaitTrue("그 방만 보인다") { list.shownIds() == listOf(roomId) }
                }
                step("상대가 그 방에 말을 걸면 그룹 칩에 안읽음 표시가 붙는다") {
                    peer.say(roomId, "$tag 그룹 방의 말")
                    awaitTrue("칩에 안읽음 표시") { groupId in list.uiState.value.groupsWithUnread }
                }
                step("관리 화면에서 이름을 바꾸면 칩 이름이 바뀐다") {
                    manage.onAction(ChatRoomGroupManageActions.RenameGroup(groupId, "$tag-새"))
                    awaitTrue("칩 이름이 바뀐다") {
                        list.uiState.value.chatGroups.firstOrNull { it.id == groupId }?.name == "$tag-새"
                    }
                }
                step("보고 있던 그룹을 지우면 칩이 사라지고 전체 보기로 돌아간다") {
                    manage.onAction(ChatRoomGroupManageActions.DeleteGroup(groupId))
                    awaitTrue("칩이 사라진다") { list.uiState.value.chatGroups.none { it.id == groupId } }
                    awaitTrue("전체 보기로 돌아간다") {
                        val search = list.uiState.value.search
                        search.filterType == Search.FilterType.ALL && search.selectedGroupId == null
                    }
                    awaitTrue("방이 다시 보인다") { roomId in list.shownIds() }
                }
            },
        )
    }
}

package com.eunilsung.talk.functest

import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.invite.InviteActions
import com.eunilsung.talk.ui.invite.InviteEvent
import com.eunilsung.talk.ui.invite.InviteMode
import com.eunilsung.talk.ui.invite.tab.group.InviteGroupActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 초대 화면의 상태 — 고른 사람으로 방이 만들어지거나 불려 오고, 그룹 탭의 찾기가 서버의 사람 정보로 맞게 걸러지는지.
 *
 * 둘만의 방은 쓰지 않는다. 둘만의 방에서 부르는 경우는 그 방의 참여자 정보만 넘겨 흉내 낸다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InviteViewModelScenarioTest {

    /** ViewModel 은 메인 스레드에서 돈다. 테스트에는 메인 스레드가 없어 그 대역([viewModelMainThread])을 세운다. */
    @BeforeTest
    fun setUp() = Dispatchers.setMain(viewModelMainThread)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun 초대화면() = functionalSuite("초대화면") { harness ->
        suspend fun membersOf(roomId: String): Set<String> =
            harness.server.room(roomId).must("방 조회").members.filterNot { it.hasLeft }.map { it.id }.toSet()

        listOf(
            scenario("골라서새방만들기") {
                val peer = harness.peer(RealServerHarness.PEER)
                val third = harness.peer(RealServerHarness.THIRD)
                val viewModel = harness.inviteViewModel()

                step("고른 사람을 다시 누르거나 칩에서 빼면 빠진다") {
                    viewModel.onAction(InviteActions.Init(InviteMode.CreateChatRoom))
                    viewModel.onAction(InviteActions.OnUserToggle(User(peer.id, peer.name)))
                    viewModel.onAction(InviteActions.OnUserToggle(User(third.id, third.name)))
                    viewModel.onAction(InviteActions.OnUserToggle(User(LATECOMER, "")))
                    viewModel.onAction(InviteActions.OnUserToggle(User(LATECOMER, "")))
                    viewModel.onAction(InviteActions.OnUserToggle(User(OUTSIDER, "")))
                    viewModel.onAction(InviteActions.OnUserRemove(OUTSIDER))
                    requireEquals(listOf(peer.id, third.id), viewModel.uiState.value.selectedUsers.map { it.id }, "고른 사람")
                }
                step("만들기를 누르면 고른 사람과 나만 있는 방이 생기고 그 방이 알려진다") {
                    viewModel.onAction(InviteActions.OnSubmit)
                    val invited = awaitEvent<InviteEvent.Invited>(viewModel.events, "방 만들기 결과")
                    harness.track(invited.chatRoomId)
                    requireEquals(setOf(RealServerHarness.ME, peer.id, third.id), membersOf(invited.chatRoomId), "방 참여자")
                    require(harness.room(invited.chatRoomId) != null, "만든 방이 목록에 없다")
                    require(!viewModel.uiState.value.isSubmitting, "보내는 중 표시가 꺼지지 않았다")
                }
            },
            scenario("있던방에부르기") {
                val roomId = harness.newRoom(RealServerHarness.PEER, RealServerHarness.THIRD)
                val viewModel = harness.inviteViewModel()

                step("있던 방에서 부르면 같은 방에 사람이 늘어난다") {
                    val existing = listOf(RealServerHarness.ME, RealServerHarness.PEER, RealServerHarness.THIRD)
                    viewModel.onAction(InviteActions.Init(InviteMode.InviteToChatRoom(roomId, existingUserIds = existing)))
                    requireEquals(existing.toSet(), viewModel.uiState.value.existingUserIds, "이미 있는 사람")
                    viewModel.onAction(InviteActions.OnUserToggle(User(LATECOMER, "")))
                    viewModel.onAction(InviteActions.OnSubmit)
                    val invited = awaitEvent<InviteEvent.Invited>(viewModel.events, "부르기 결과")
                    requireEquals(roomId, invited.chatRoomId, "알린 방")
                    require(LATECOMER in membersOf(roomId), "부른 사람이 서버의 방에 없다")
                    awaitTrue("목록의 참여자 수가 는다") { harness.room(roomId)?.displayUserCount == 4 }
                }
            },
            scenario("둘만의방에서부르기") {
                val peer = harness.peer(RealServerHarness.PEER)
                val viewModel = harness.inviteViewModel()

                step("둘만의 방에서 부르면 그 방 대신 셋이 쓰는 새 방이 생긴다") {
                    viewModel.onAction(
                        InviteActions.Init(
                            InviteMode.InviteToChatRoom(
                                chatRoomId = DIRECT_ROOM,
                                existingUserIds = listOf(RealServerHarness.ME, peer.id),
                                createNewRoom = true,
                                existingUsers = listOf(RealServerHarness.ME to "", peer.id to peer.name),
                            )
                        )
                    )
                    viewModel.onAction(InviteActions.OnUserToggle(User(RealServerHarness.THIRD, "")))
                    viewModel.onAction(InviteActions.OnSubmit)
                    val invited = awaitEvent<InviteEvent.Invited>(viewModel.events, "새 방 결과")
                    harness.track(invited.chatRoomId)
                    require(invited.chatRoomId != DIRECT_ROOM, "새 방이 아니라 원래 방이 알려졌다")
                    requireEquals(
                        setOf(RealServerHarness.ME, peer.id, RealServerHarness.THIRD),
                        membersOf(invited.chatRoomId),
                        "새 방 참여자",
                    )
                }
            },
            scenario("그룹탭찾기") {
                val viewModel = harness.inviteGroupViewModel()

                step("그룹 탭에 서버의 사람이 채워진다") {
                    harness.groups.fetchGroups()
                    awaitTrue("상대가 보인다") {
                        viewModel.uiState.value.items.any { group -> group.userData.any { it.userId == RealServerHarness.PEER } }
                    }
                }
                step("전화번호로 찾으면 맞는 사람만 남고 그 그룹이 펼쳐진다") {
                    viewModel.onAction(InviteGroupActions.OnSearchQueryChange(RealServerHarness.PEER_PHONE))
                    awaitTrue("상대만 남는다") {
                        val people = viewModel.uiState.value.items.flatMap { it.userData }
                        people.isNotEmpty() && people.all { it.userId == RealServerHarness.PEER }
                    }
                    require(viewModel.uiState.value.items.all { it.isExpanded }, "찾은 그룹이 펼쳐지지 않았다")
                }
                step("찾는 말을 지우면 다시 모든 사람이 보이고 그룹을 눌러 접고 펼 수 있다") {
                    viewModel.onAction(InviteGroupActions.OnClearSearch)
                    awaitTrue("다른 사람도 보인다") {
                        viewModel.uiState.value.items.flatMap { it.userData }.any { it.userId == RealServerHarness.THIRD }
                    }
                    val group = viewModel.uiState.value.items.first { it.userData.isNotEmpty() }
                    viewModel.onAction(InviteGroupActions.ToggleGroup(group.id))
                    awaitTrue("그 그룹만 뒤집힌다") {
                        viewModel.uiState.value.items.first { it.id == group.id }.isExpanded == !group.isExpanded
                    }
                }
            },
        )
    }

    private companion object {
        const val LATECOMER = "test4"
        const val OUTSIDER = "test5"
        const val DIRECT_ROOM = "둘만의-방"
    }
}

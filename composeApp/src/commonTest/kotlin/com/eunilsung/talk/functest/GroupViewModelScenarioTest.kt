package com.eunilsung.talk.functest

import com.eunilsung.talk.domain.model.AddUserResult
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.util.GroupNameValidation
import com.eunilsung.talk.ui.group.GroupActions
import com.eunilsung.talk.ui.group.GroupEvent
import com.eunilsung.talk.ui.group.GroupMode
import com.eunilsung.talk.ui.group.GroupViewModel
import com.eunilsung.talk.ui.userprofile.UserProfileActions
import com.eunilsung.talk.ui.userprofile.UserProfileUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 내 그룹 화면과 프로필 화면의 상태 — 그룹 만들기·이름·사람 옮기기·찾기·대화 시작이 서버를 거쳐 맞게 보이는지.
 *
 * 저장소가 서버에 남기는 것은 [GroupAndInviteScenarioTest] 가 본다. 여기서는 화면이 막는 것과 알리는 것을 본다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelScenarioTest {

    /** ViewModel 은 메인 스레드에서 돈다. 테스트에는 메인 스레드가 없어 그 대역([viewModelMainThread])을 세운다. */
    @BeforeTest
    fun setUp() = Dispatchers.setMain(viewModelMainThread)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun 내그룹화면() = functionalSuite("내그룹화면") { harness ->
        fun GroupViewModel.group(name: String): Group.Item? = uiState.value.items.firstOrNull { it.name == name }
        fun GroupViewModel.people(): List<Group.User> = uiState.value.items.flatMap { it.userData }
        suspend fun onServer(name: String) =
            harness.server.contactGroups().must("연락처 그룹 조회").firstOrNull { it.name == name }

        listOf(
            scenario("그룹만들고사람옮기기") {
                val tag = groupTag()
                val first = "${tag}가"
                val second = "${tag}나"
                val viewModel = harness.groupViewModel()

                step("서버의 사람이 화면에 채워진다") {
                    viewModel.onAction(GroupActions.FetchGroups)
                    awaitTrue("상대가 보인다") { viewModel.people().any { it.userId == RealServerHarness.PEER } }
                }
                step("빈 이름으로 만들면 서버에 묻지 않고 거절된다") {
                    viewModel.onAction(GroupActions.CreateGroup("   "))
                    val invalid = awaitEvent<GroupEvent.CreateInvalid>(viewModel.events, "빈 이름 거절")
                    requireEquals(GroupNameValidation.Empty, invalid.reason, "거절 사유")
                }
                step("만들면 결과가 알려지고 화면과 서버에 생긴다") {
                    listOf(first, second).forEach { name ->
                        viewModel.onAction(GroupActions.CreateGroup(name))
                        val created = awaitEvent<GroupEvent.GroupCreated>(viewModel.events, "만들기 결과 $name")
                        requireEquals(GroupEvent.GroupCreated(true, name), created, "알린 결과")
                        awaitTrue("화면에 생긴다 $name") { viewModel.group(name) != null }
                        require(onServer(name) != null, "서버에 그룹이 없다: $name")
                    }
                }
                step("다른 그룹과 같은 이름으로 바꾸면 서버에 묻지 않고 거절된다") {
                    viewModel.onAction(GroupActions.RenameGroup(viewModel.group(first)!!.id, second))
                    val invalid = awaitEvent<GroupEvent.RenameInvalid>(viewModel.events, "같은 이름 거절")
                    requireEquals(GroupNameValidation.Duplicate, invalid.reason, "거절 사유")
                    require(onServer(first) != null, "거절했는데 서버의 이름이 바뀌었다")
                }
                step("사람을 다른 그룹으로 옮기면 화면과 서버에서 함께 옮겨진다") {
                    val from = viewModel.group(first)!!.id
                    requireEquals(AddUserResult.SUCCESS, harness.groups.copyUserToGroup(RealServerHarness.PEER, from), "담기 결과")
                    awaitTrue("첫 그룹에 보인다") {
                        viewModel.group(first)?.userData?.any { it.userId == RealServerHarness.PEER } == true
                    }
                    val to = viewModel.group(second)!!.id
                    viewModel.onAction(GroupActions.MoveUserToGroup(RealServerHarness.PEER, from, to))
                    val moved = awaitEvent<GroupEvent.UserMoved>(viewModel.events, "옮기기 결과")
                    requireEquals(GroupEvent.UserMoved(true, to), moved, "알린 결과")
                    awaitTrue("화면에서 옮겨진다") {
                        viewModel.group(first)?.userData?.none { it.userId == RealServerHarness.PEER } == true &&
                            viewModel.group(second)?.userData?.any { it.userId == RealServerHarness.PEER } == true
                    }
                    requireEquals(emptyList<String>(), onServer(first)?.memberIds, "서버의 첫 그룹 구성원")
                    requireEquals(listOf(RealServerHarness.PEER), onServer(second)?.memberIds, "서버의 둘째 그룹 구성원")
                }
                step("그룹에서 빼면 결과가 알려지고 서버에서도 빠진다") {
                    viewModel.onAction(GroupActions.RemoveUserFromGroup(RealServerHarness.PEER, viewModel.group(second)!!.id))
                    val removed = awaitEvent<GroupEvent.UserRemoved>(viewModel.events, "빼기 결과")
                    requireEquals(GroupEvent.UserRemoved(true), removed, "알린 결과")
                    requireEquals(emptyList<String>(), onServer(second)?.memberIds, "서버의 구성원")
                }
                step("이름을 바꾸고 지우면 화면과 서버가 따라간다") {
                    val renamed = "${tag}다"
                    viewModel.onAction(GroupActions.RenameGroup(viewModel.group(first)!!.id, renamed))
                    requireEquals(
                        GroupEvent.GroupRenamed(true, renamed),
                        awaitEvent<GroupEvent.GroupRenamed>(viewModel.events, "이름 바꾸기 결과"),
                        "알린 결과",
                    )
                    awaitTrue("화면의 이름이 바뀐다") { viewModel.group(renamed) != null }
                    require(onServer(renamed) != null, "서버의 이름이 바뀌지 않았다")

                    viewModel.onAction(GroupActions.DeleteGroup(viewModel.group(renamed)!!.id))
                    requireEquals(
                        GroupEvent.GroupDeleted(true),
                        awaitEvent<GroupEvent.GroupDeleted>(viewModel.events, "지우기 결과"),
                        "알린 결과",
                    )
                    awaitTrue("화면에서 사라진다") { viewModel.group(renamed) == null }
                    require(onServer(renamed) == null, "지운 그룹이 서버에 남아 있다")
                }
            },
            scenario("찾기와대화시작") {
                val peer = harness.peer(RealServerHarness.PEER)
                val third = harness.peer(RealServerHarness.THIRD)
                val viewModel = harness.groupViewModel()

                step("전화번호로 찾으면 맞는 사람만 남고 지우면 다시 모두 보인다") {
                    viewModel.onAction(GroupActions.FetchGroups)
                    awaitTrue("셋째가 보인다") { viewModel.people().any { it.userId == third.id } }
                    viewModel.onAction(GroupActions.OnSearchQueryChange(RealServerHarness.PEER_PHONE))
                    awaitTrue("상대만 남는다") {
                        viewModel.people().isNotEmpty() && viewModel.people().all { it.userId == peer.id }
                    }
                    viewModel.onAction(GroupActions.OnClearSearch)
                    awaitTrue("셋째가 다시 보인다") { viewModel.people().any { it.userId == third.id } }
                }
                step("편집 모드에서 고른 사람과 대화를 시작하면 방이 생기고 편집 모드가 끝난다") {
                    viewModel.onAction(GroupActions.SetMode(GroupMode.EDIT))
                    viewModel.onAction(GroupActions.OnSelectUser(User(peer.id, peer.name)))
                    viewModel.onAction(GroupActions.OnSelectUser(User(third.id, third.name)))
                    requireEquals(setOf(peer.id, third.id), viewModel.uiState.value.selectedUserIds, "고른 사람")

                    viewModel.onAction(GroupActions.StartChat(listOf(peer.id to peer.name, third.id to third.name)))
                    val started = awaitEvent<GroupEvent.ChatStarted>(viewModel.events, "대화 시작 결과")
                    harness.track(started.chatRoomId)
                    val members = harness.server.room(started.chatRoomId).must("방 조회").members.map { it.id }.toSet()
                    requireEquals(setOf(RealServerHarness.ME, peer.id, third.id), members, "방 참여자")
                    require(!viewModel.uiState.value.isEditMode, "편집 모드가 끝나지 않았다")
                    requireEquals(emptySet<String>(), viewModel.uiState.value.selectedUserIds, "남은 선택")
                }
            },
        )
    }

    @Test
    fun 프로필화면() = functionalSuite("프로필화면") { harness ->
        listOf(
            scenario("프로필열기") {
                val viewModel = harness.userProfileViewModel()
                fun shown(): UserProfileUiState.Success? = viewModel.uiState.value as? UserProfileUiState.Success

                step("다른 사람을 열면 서버의 프로필이 보이고 내 것으로 표시되지 않는다") {
                    viewModel.onAction(UserProfileActions.Load(RealServerHarness.PEER))
                    awaitTrue("서버의 프로필") { shown()?.let { it.profile.userId == RealServerHarness.PEER && !it.isStale } == true }
                    require(shown()!!.profile.name.isNotBlank(), "이름이 비어 있다")
                    require(!shown()!!.isMe, "남의 프로필이 내 것으로 표시된다")
                }
                step("나를 열면 내 프로필로 표시된다") {
                    viewModel.onAction(UserProfileActions.Load(RealServerHarness.ME))
                    awaitTrue("내 프로필") { shown()?.let { it.profile.userId == RealServerHarness.ME && !it.isStale } == true }
                    require(shown()!!.isMe, "내 프로필이 내 것으로 표시되지 않는다")
                }
                step("없는 사람을 열면 받지 못했다고 보인다") {
                    viewModel.onAction(UserProfileActions.Load(UNKNOWN_USER))
                    awaitTrue("받지 못함") { viewModel.uiState.value == UserProfileUiState.Error }
                }
            },
        )
    }

    /** 그룹 이름은 14자까지라 이번 실행의 이름표를 줄여 쓴다. 끝날 때 정리되도록 머리말은 그대로다. */
    private fun groupTag(): String = "${RealServerHarness.TAG_PREFIX}${nowMillis() % 100_000_000}"

    private companion object {
        const val UNKNOWN_USER = "no-such-user"
    }
}

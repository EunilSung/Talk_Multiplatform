package com.eunilsung.talk.ui.quickactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalInspectionMode
import cafe.adriel.voyager.navigator.Navigator
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.default_group
import multiplatformtalk.composeapp.generated.resources.toast_add_to_group_failed
import multiplatformtalk.composeapp.generated.resources.toast_added_to_group
import multiplatformtalk.composeapp.generated.resources.toast_user_already_in_group
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.MY_PROFILE_GROUP_ID
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.repository.AddUserResult
import com.eunilsung.talk.domain.repository.ChatRoomListRepository
import com.eunilsung.talk.domain.repository.GroupRepository
import com.eunilsung.talk.domain.repository.UserProfileRepository
import com.eunilsung.talk.domain.usecase.InviteUseCases
import com.eunilsung.talk.ui.chatroom.ChatRoomScreen
import com.eunilsung.talk.ui.main.EmptyScreen
import com.eunilsung.talk.ui.main.LocalRightNavigator
import com.eunilsung.talk.ui.uikit.dialog.DialogManager
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import com.eunilsung.talk.ui.uikit.toast.ToastManager
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import com.eunilsung.talk.util.UserListCodec
import org.koin.compose.koinInject

/** 리스트 항목(사용자/그룹) 대상 빠른 액션의 단일 진입점. */
class QuickActions(
    // 의존성은 모두 nullable — Preview 에서는 null 이며 메서드는 no-op.
    private val rightNavigator: Navigator?,
    private val groupRepository: GroupRepository?,
    private val chatRoomListRepository: ChatRoomListRepository?,
    private val inviteUseCases: InviteUseCases?,
    private val userProfileRepository: UserProfileRepository?,
    private val toastManager: ToastManager?,
    private val coroutineScope: CoroutineScope,
    private val txtDefaultGroup: String
) {
    /** 1:1 대화 시작 — 기존 방이 있으면 입장, 없으면 생성 후 입장. targetUserIds 는 1명만 유효. */
    fun startChat(targetUserIds: List<String>) {
        if (rightNavigator == null) return
        val invites = inviteUseCases ?: return
        val cleaned = targetUserIds.filter { it.isNotBlank() }.distinct()
        if (cleaned.size != 1) {
            return
        }
        val targetId = cleaned.first()
        val myId = com.eunilsung.talk.Config.MyInfo.userId

        coroutineScope.launch {
            val existing = findOneToOneChatRoom(targetUserId = targetId, myId = myId)
            if (existing != null) {
                navigateToChatRoom(existing.id)
                return@launch
            }

            if (targetId == myId) {
                toastManager?.show("나와의 대화방이 없습니다")
                return@launch
            }

            val name = resolveUserName(targetId)
            val newRoomId = runCatching {
                invites.inviteUsers(
                    chatRoomId = "",
                    invitedUsers = listOf(targetId to name),
                    existingUserCount = 0,
                )
            }.getOrNull()

            if (newRoomId.isNullOrBlank()) {
                toastManager?.show("대화방 생성에 실패했습니다")
                return@launch
            }
            navigateToChatRoom(newRoomId)
        }
    }

    /** 기존 1:1 대화방 검색 — 로컬 대화방 리스트 스냅샷에서 조건 매칭. */
    private suspend fun findOneToOneChatRoom(
        targetUserId: String,
        myId: String,
    ): ChatRoom.Item? {
        val repo = chatRoomListRepository ?: return null
        val rooms = runCatching { repo.getChatRooms().first() }
            .getOrElse { emptyList() }
        if (rooms.isEmpty()) return null

        val expectedCount = if (targetUserId == myId) "1" else "2"
        return rooms.firstOrNull { room ->
            room.totalUserCount == expectedCount
                    && !room.id.contains("$")
                    && UserListCodec.contains(room.totalUserList, targetUserId)
        }
    }


    /** userId 로 사용자 이름 조회 — 캐시 우선, 없으면 프로필 조회, 실패 시 빈 문자열. */
    private suspend fun resolveUserName(userId: String): String {
        val repo = userProfileRepository ?: return ""
        val cached = runCatching { repo.getCachedProfile(userId) }.getOrNull()
        if (cached != null && cached.name.isNotBlank()) return cached.name
        val fetched = runCatching { repo.fetchProfile(userId) }
            .getOrNull()?.getOrNull()
        return fetched?.name.orEmpty()
    }

    /** ChatRoomScreen 으로 진입 — Empty 면 push, 다른 화면이면 replace, 동일 방이면 no-op. */
    private fun navigateToChatRoom(chatRoomId: String) {
        val nav = rightNavigator ?: return
        val current = nav.lastItem
        if (current is ChatRoomScreen && current.chatRoomId == chatRoomId) return

        val newScreen = ChatRoomScreen(chatRoomId = chatRoomId)
        if (current === EmptyScreen) {
            nav.push(newScreen)
        } else {
            nav.replace(newScreen)
        }
    }

    /** 사용자를 그룹에 추가하고 결과별 토스트 표시. */
    fun addUserToGroup(userId: String, targetGroupId: String) {
        val repo = groupRepository ?: return
        coroutineScope.launch {
            when (repo.copyUserToGroup(userId, targetGroupId)) {
                AddUserResult.SUCCESS -> {
                    val groups = repo.getGroups().first()
                    val target = groups.firstOrNull { it.id == targetGroupId }
                    val displayName = when {
                        target == null -> ""
                        target.id == "0" && target.alineCode == "-3" -> txtDefaultGroup
                        else -> target.name
                    }
                    toastManager?.show(getString(Res.string.toast_added_to_group, displayName))
                }
                AddUserResult.ALREADY_EXISTS -> {
                    toastManager?.show(getString(Res.string.toast_user_already_in_group))
                }
                AddUserResult.FAILED -> {
                    toastManager?.show(getString(Res.string.toast_add_to_group_failed))
                }
            }
        }
    }

}

@Composable
fun rememberQuickActions(): QuickActions {
    val isPreview = LocalInspectionMode.current
    val rightNavigator: Navigator? = if (isPreview) null else LocalRightNavigator.current
    val groupRepository: GroupRepository? = if (isPreview) null else koinInject()
    val chatRoomListRepository: ChatRoomListRepository? =
        if (isPreview) null else koinInject()
    val inviteUseCases: InviteUseCases? = if (isPreview) null else koinInject()
    val userProfileRepository: UserProfileRepository? =
        if (isPreview) null else koinInject()
    val toastManager: ToastManager? = if (isPreview) null else LocalToastManager.current
    val scope = rememberCoroutineScope()
    val txtDefaultGroup = if (isPreview) "" else stringResource(Res.string.default_group)
    return remember(
        rightNavigator,
        groupRepository,
        chatRoomListRepository,
        inviteUseCases,
        userProfileRepository,
        toastManager,
        scope,
        txtDefaultGroup
    ) {
        QuickActions(
            rightNavigator = rightNavigator,
            groupRepository = groupRepository,
            chatRoomListRepository = chatRoomListRepository,
            inviteUseCases = inviteUseCases,
            userProfileRepository = userProfileRepository,
            toastManager = toastManager,
            coroutineScope = scope,
            txtDefaultGroup = txtDefaultGroup,
        )
    }
}

/** 그룹 추가/복사/이동 시 띄울 대상 그룹 picker 람다. */
@Composable
fun rememberGroupPicker(): (title: String, onPicked: (String) -> Unit) -> Unit {
    if (LocalInspectionMode.current) {
        return remember { { _, _ -> /* no-op */ } }
    }
    val dialogManager: DialogManager = LocalDialogManager.current
    val groupRepository: GroupRepository = koinInject()
    val groups by groupRepository.getGroups().collectAsState(initial = emptyList())
    val txtDefaultGroup = stringResource(Res.string.default_group)

    return remember(groups, dialogManager, txtDefaultGroup) {
        { title, onPicked ->
            val targets = groups.filter {
                it.id != MY_PROFILE_GROUP_ID && !it.id.contains("-")
            }
            val labels = targets.map { g ->
                if (g.id == "0" && g.alineCode == "-3") txtDefaultGroup else g.name
            }
            val labelToId = labels.zip(targets.map { it.id }).toMap()
            dialogManager.list(
                title = title,
                items = labels,
                onSelected = { picked ->
                    labelToId[picked]?.let { id -> onPicked(id) }
                }
            )
        }
    }
}

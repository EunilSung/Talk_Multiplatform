package com.eunilsung.talk.ui.quickactions

import com.eunilsung.talk.domain.model.isSystemGroup
import com.eunilsung.talk.domain.model.isMyProfileGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import cafe.adriel.voyager.navigator.Navigator
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.default_group
import multiplatformtalk.composeapp.generated.resources.toast_chat_create_failed
import multiplatformtalk.composeapp.generated.resources.toast_add_to_group_failed
import multiplatformtalk.composeapp.generated.resources.toast_added_to_group
import multiplatformtalk.composeapp.generated.resources.toast_user_already_in_group
import com.eunilsung.talk.domain.model.isDefaultGroup
import com.eunilsung.talk.ui.chatroom.ChatRoomScreen
import com.eunilsung.talk.ui.main.EmptyScreen
import com.eunilsung.talk.ui.main.LocalRightNavigator
import com.eunilsung.talk.ui.uikit.dialog.DialogManager
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** 리스트 항목(사용자/그룹) 대상 빠른 액션의 단일 진입점. 일은 [QuickActionsViewModel] 이 한다. */
class QuickActions(
    private val onAction: (QuickActionsActions) -> Unit,
) {
    /** 1:1 대화 시작 — 기존 방이 있으면 입장, 없으면 생성 후 입장. targetUserIds 는 1명만 유효. */
    fun startChat(targetUserIds: List<String>) {
        onAction(QuickActionsActions.StartChat(targetUserIds))
    }

    /** 사용자를 그룹에 추가하고 결과별 토스트 표시. */
    fun addUserToGroup(userId: String, targetGroupId: String) {
        onAction(QuickActionsActions.AddUserToGroup(userId, targetGroupId))
    }
}

/** Preview 에서는 아무것도 하지 않는 [QuickActions] 를 돌려준다. */
@Composable
fun rememberQuickActions(): QuickActions {
    if (LocalInspectionMode.current) {
        return remember { QuickActions {} }
    }
    val viewModel: QuickActionsViewModel = koinViewModel()
    val rightNavigator = LocalRightNavigator.current
    val toastManager = LocalToastManager.current
    val txtDefaultGroup = stringResource(Res.string.default_group)

    LaunchedEffect(viewModel, rightNavigator, toastManager, txtDefaultGroup) {
        viewModel.events.collect { event ->
            when (event) {
                is QuickActionsEvent.OpenChatRoom -> rightNavigator.openChatRoom(event.chatRoomId)
                is QuickActionsEvent.ChatCreateFailed ->
                    toastManager.show(getString(Res.string.toast_chat_create_failed))
                is QuickActionsEvent.AddedToGroup -> {
                    val target = event.group
                    val displayName = when {
                        target == null -> ""
                        target.isDefaultGroup -> txtDefaultGroup
                        else -> target.name
                    }
                    toastManager.show(getString(Res.string.toast_added_to_group, displayName))
                }
                is QuickActionsEvent.AlreadyInGroup ->
                    toastManager.show(getString(Res.string.toast_user_already_in_group))
                is QuickActionsEvent.AddToGroupFailed ->
                    toastManager.show(getString(Res.string.toast_add_to_group_failed))
            }
        }
    }
    return remember(viewModel) { QuickActions(viewModel::onAction) }
}

/** ChatRoomScreen 으로 진입 — Empty 면 push, 다른 화면이면 replace, 동일 방이면 no-op. */
private fun Navigator.openChatRoom(chatRoomId: String) {
    val current = lastItem
    if (current is ChatRoomScreen && current.chatRoomId == chatRoomId) return

    val newScreen = ChatRoomScreen(chatRoomId = chatRoomId)
    if (current is EmptyScreen) {
        push(newScreen)
    } else {
        replace(newScreen)
    }
}

/** 그룹 추가/복사/이동 시 띄울 대상 그룹 picker 람다. */
@Composable
fun rememberGroupPicker(): (title: String, onPicked: (String) -> Unit) -> Unit {
    if (LocalInspectionMode.current) {
        return remember { { _, _ -> /* no-op */ } }
    }
    val dialogManager: DialogManager = LocalDialogManager.current
    val viewModel: QuickActionsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val groups = uiState.groups
    val txtDefaultGroup = stringResource(Res.string.default_group)

    return remember(groups, dialogManager, txtDefaultGroup) {
        { title, onPicked ->
            val targets = groups.filter {
                !it.isMyProfileGroup && !it.isSystemGroup
            }
            val labels = targets.map { g ->
                if (g.isDefaultGroup) txtDefaultGroup else g.name
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

package com.eunilsung.talk.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import multiplatformtalk.composeapp.generated.resources.*
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.domain.usecase.InviteUseCases
import com.eunilsung.talk.ui.chatroom.ChatRoomScreen
import com.eunilsung.talk.ui.main.LocalRightNavigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.Search
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.model.isDefaultGroup
import com.eunilsung.talk.domain.util.GROUP_NAME_MAX_LENGTH
import com.eunilsung.talk.domain.util.GroupNameValidation
import com.eunilsung.talk.ui.group.item.GroupItemList
import com.eunilsung.talk.ui.group.item.menu.rememberGroupItemMenu
import com.eunilsung.talk.ui.group.item.menu.rememberGroupUserItemMenu
import com.eunilsung.talk.ui.userprofile.LocalShowUserProfile
import com.eunilsung.talk.ui.main.LocalRightPaneOpen
import com.eunilsung.talk.ui.uikit.BackHandler
import com.eunilsung.talk.ui.uikit.EditModeIconButton
import com.eunilsung.talk.ui.uikit.rememberMoveToBackground
import com.eunilsung.talk.ui.uikit.button.ButtonV1
import com.eunilsung.talk.ui.uikit.line.LineDivider
import com.eunilsung.talk.ui.uikit.sheet.BottomSheet
import com.eunilsung.talk.ui.uikit.textfield.TextFieldV1
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import com.eunilsung.talk.ui.uikit.toast.ToastManager
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

object GroupScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: GroupViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val searchState by viewModel.searchState.collectAsState()
        val selectedUserIds by viewModel.selectedUserIds.collectAsState()
        val toastManager = LocalToastManager.current
        val txtDefaultGroup = stringResource(Res.string.default_group)

        val rightNavigator = LocalRightNavigator.current
        val inviteUseCases: InviteUseCases = koinInject()
        val scope = rememberCoroutineScope()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                handleGroupEvent(
                    event = event,
                    groups = viewModel.uiState.value.items,
                    defaultGroupName = txtDefaultGroup,
                    toastManager = toastManager,
                )
            }
        }

        GroupContent(
            uiState = uiState,
            searchState = searchState,
            selectedUserIds = selectedUserIds,
            onAction = viewModel::onAction,
            onEmptySelection = {
                scope.launch { toastManager.show(getString(Res.string.toast_no_user_selected)) }
            },
            onStartChat = { userPairs ->
                scope.launch {
                    val roomId = runCatching {
                        inviteUseCases.inviteUsers("", userPairs, 0)
                    }.getOrNull()
                    if (!roomId.isNullOrBlank()) {
                        rightNavigator.push(ChatRoomScreen(chatRoomId = roomId))
                        viewModel.onAction(GroupActions.SetMode(GroupMode.IDLE))
                    } else {
                        toastManager.show(getString(Res.string.toast_chat_create_failed))
                    }
                }
            },
        )
    }
}

private suspend fun handleGroupEvent(
    event: GroupEvent,
    groups: List<Group.Item>,
    defaultGroupName: String,
    toastManager: ToastManager,
) {
    when (event) {
        is GroupEvent.UserRemoved -> toastManager.show(
            getString(
                if (event.success) Res.string.toast_user_removed_from_group
                else Res.string.toast_remove_from_group_failed
            )
        )
        is GroupEvent.UserMoved -> {
            if (event.success) {
                val target = groups.firstOrNull { it.id == event.targetGroupId }
                val displayName = when {
                    target == null -> ""
                    target.isDefaultGroup -> defaultGroupName
                    else -> target.name
                }
                toastManager.show(getString(Res.string.toast_user_moved_to_group, displayName))
            } else {
                toastManager.show(getString(Res.string.toast_move_group_failed))
            }
        }
        is GroupEvent.GroupRenamed -> toastManager.show(
            if (event.success) getString(Res.string.toast_group_renamed, event.newName)
            else getString(Res.string.toast_group_rename_failed)
        )
        is GroupEvent.RenameInvalid -> {
            val msg = when (event.reason) {
                GroupNameValidation.Empty -> getString(Res.string.group_name_invalid_empty)
                GroupNameValidation.TooLong -> getString(Res.string.group_name_invalid_too_long, GROUP_NAME_MAX_LENGTH)
                GroupNameValidation.InvalidChars -> getString(Res.string.group_name_invalid_chars)
                GroupNameValidation.Duplicate -> getString(Res.string.group_name_invalid_duplicate)
                GroupNameValidation.Valid -> return
            }
            toastManager.show(msg)
        }
        is GroupEvent.GroupDeleted -> toastManager.show(
            getString(
                if (event.success) Res.string.toast_group_deleted
                else Res.string.toast_group_delete_failed
            )
        )
        is GroupEvent.GroupCreated -> toastManager.show(
            if (event.success) getString(Res.string.toast_group_created, event.name)
            else getString(Res.string.toast_group_create_failed)
        )
        is GroupEvent.CreateInvalid -> {
            if (event.reason == GroupNameValidation.Empty) {
                toastManager.show(getString(Res.string.group_name_invalid_empty))
            }
        }
    }
}

@Composable
private fun GroupBackHandler(
    isEditMode: Boolean,
    onExitEditMode: () -> Unit,
) {
    val moveToBackground = rememberMoveToBackground()
    val rightPaneOpen = LocalRightPaneOpen.current
    BackHandler(enabled = isEditMode || !rightPaneOpen) {
        when {
            isEditMode -> onExitEditMode()
            else -> moveToBackground()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupContent(
    uiState: GroupUiState,
    searchState: Search.State,
    selectedUserIds: Set<String>,
    onAction: (GroupActions) -> Unit,
    onStartChat: (List<Pair<String, String>>) -> Unit = {},
    onEmptySelection: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val showUserProfile = LocalShowUserProfile.current

    val selectedUsers = remember(uiState.items, selectedUserIds) {
        uiState.items
            .flatMap { it.userData }
            .mapNotNull { u -> u.userId?.takeIf { it.isNotBlank() && selectedUserIds.contains(it) }?.let { id -> id to u.userName.orEmpty() } }
            .distinctBy { it.first }
    }

    GroupBackHandler(
        isEditMode = uiState.isEditMode,
        onExitEditMode = { onAction(GroupActions.SetMode(GroupMode.IDLE)) },
    )

    var showCreateGroupSheet by remember { mutableStateOf(false) }
    val groupHeaderItemMenu = rememberGroupItemMenu(
        showCreateGroupDialog = { showCreateGroupSheet = true },
        onAction = onAction
    )
    val groupUserItemMenu = rememberGroupUserItemMenu(onAction = onAction)

    GroupSearchScaffold(
        listState = listState,
        searchQuery = searchState.query,
        onSearchChange = { onAction(GroupActions.OnSearchQueryChange(it)) },
        onClearSearch = { onAction(GroupActions.OnClearSearch) },
        actions = {
            EditModeIconButton(
                isEditMode = uiState.isEditMode,
                onToggle = {
                    val next = if (uiState.isEditMode) GroupMode.IDLE else GroupMode.EDIT
                    onAction(GroupActions.SetMode(next))
                },
            )
            Spacer(modifier = Modifier.size(15.dp))
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Bg)
                .padding(padding)
        ) {
            GroupItemList(
                groups = uiState.items,
                listState = listState,
                isToggleable = uiState.isEditMode,
                userOf = { member -> groupUserOf(member, selectedUserIds) },
                onExpand = { onAction(GroupActions.ToggleGroup(it)) },
                onUserClick = { _, member ->
                    if (uiState.isEditMode) onAction(GroupActions.OnSelectUser(groupUserOf(member, selectedUserIds)))
                    else showUserProfile(member.userId ?: "")
                },
                onUserLongClick = { group, member ->
                    groupUserItemMenu.onLongClick(groupUserOf(member, selectedUserIds), group.id)
                },
                onHeaderOption = { group -> groupHeaderItemMenu.onLongClickHandler(group) },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp),
            )

            if (uiState.isEditMode){
                LineDivider(modifier = Modifier.fillMaxWidth())
                GroupStartChatBar(
                    onStartChat = {
                        if (selectedUsers.isEmpty()) onEmptySelection() else onStartChat(selectedUsers)
                    },
                )
            }
        }
    }

    CreateGroupBottomSheet(
        visible = showCreateGroupSheet,
        onDismiss = { showCreateGroupSheet = false },
        onConfirm = { rawInput ->
            onAction(GroupActions.CreateGroup(rawInput))
            showCreateGroupSheet = false
        }
    )
}

private fun groupUserOf(member: Group.User, selectedUserIds: Set<String>): User {
    val userId = member.userId ?: ""
    return User(
        id = userId,
        name = member.userName ?: "",
        departmentName = member.departmentName,
        positionName = member.positionName,
        nickname = member.alias?.takeIf { it.isNotBlank() } ?: member.statusMessage,
        isSelect = selectedUserIds.contains(userId),
        presencePc = member.pcStatus?.takeIf { it.isNotBlank() },
        presenceMobile = member.mobileStatus?.takeIf { it.isNotBlank() },
    )
}

@Composable
private fun CreateGroupBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    LaunchedEffect(visible) {
        if (!visible) input = ""
    }

    BottomSheet(
        isVisible = visible,
        onDismiss = onDismiss,
        isModal = true,
    ) {
        CreateGroupSheetContent(
            title = stringResource(Res.string.group_add),
            input = input,
            onInputChange = { input = it },
            onCancel = onDismiss,
            onConfirm = { onConfirm(input) },
        )
    }
}

@Composable
private fun CreateGroupSheetContent(
    title: String,
    input: String,
    onInputChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = title,
            color = AppColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = stringResource(Res.string.group_name),
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.Text,
            fontSize = 13.sp,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(5.dp))

        TextFieldV1(
            inputText = input,
            hintText = stringResource(Res.string.group_name_hint),
            singleLine = true,
            onValueChange = onInputChange,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ButtonV1(
                modifier = Modifier.weight(1f).height(46.dp),
                text = stringResource(Res.string.cancel),
                containerColor = AppColors.DialogCancelBtnBg,
                contentColor = AppColors.TextSub,
                onClick = onCancel,
            )
            ButtonV1(
                modifier = Modifier.weight(1f).height(46.dp),
                text = stringResource(Res.string.ok),
                onClick = onConfirm,
            )
        }
    }
}

/** 편집 모드 하단 액션 바 — 선택한 사용자들과 대화 시작. */
@Composable
fun GroupStartChatBar(
    onStartChat: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.BgSub_2)
            .padding(10.dp)
    ) {
        ButtonV1(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            text = stringResource(Res.string.chat_do),
            containerColor = AppColors.PrimaryMain,
            onClick = onStartChat
        )
    }
}

private fun sampleGroup() = Group.Item(
    id = "1",
    name = "그룹1",
    alineCode = "1",
    userData = listOf(Group.User(
        userId = "1",
        userName = "Eunil Sung",
        departmentName = "Department",
        positionName = "Position",
        statusMessage = "StatusMessage"
    )),
    isExpanded = true
)

@Preview(showBackground = true)
@Composable
fun GroupScreenPreview() {
    MaterialTheme {
        GroupContent(
            uiState = GroupUiState.Idle(items = listOf(sampleGroup()), isEditMode = true),
            searchState = Search.State(),
            selectedUserIds = emptySet(),
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GroupStartChatBarPreview() {
    MaterialTheme {
        GroupStartChatBar()
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateGroupSheetContentPreview() {
    MaterialTheme {
        CreateGroupSheetContent(
            title = "그룹 추가",
            input = "개발팀",
            onInputChange = {},
            onCancel = {},
            onConfirm = {}
        )
    }
}

package com.eunilsung.talk.ui.chatroomlist

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import multiplatformtalk.composeapp.generated.resources.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.Search
import com.eunilsung.talk.ui.chatroomlist.group.ChatRoomGroupManageScreen
import com.eunilsung.talk.ui.chatroom.ChatRoomScreen
import com.eunilsung.talk.ui.invite.InviteMode
import com.eunilsung.talk.ui.invite.InviteScreen
import com.eunilsung.talk.ui.main.EmptyScreen
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.main.LocalRightNavigator
import com.eunilsung.talk.ui.main.LocalRightPaneOpen
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.BackHandler
import com.eunilsung.talk.ui.uikit.EditModeIconButton
import com.eunilsung.talk.ui.uikit.RefreshIconButton
import com.eunilsung.talk.ui.uikit.button.ButtonV1
import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.ui.uikit.chip.ChipItem
import com.eunilsung.talk.ui.uikit.chip.ChipRow
import com.eunilsung.talk.ui.uikit.line.LineDivider
import com.eunilsung.talk.ui.uikit.rememberMoveToBackground
import com.eunilsung.talk.ui.uikit.search.SearchBar
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import com.eunilsung.talk.ui.uikit.toast.ToastManager
import com.eunilsung.talk.ui.chatroomlist.item.ChatRoomItemList
import com.eunilsung.talk.ui.chatroomlist.item.menu.rememberChatRoomItemMenu
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

object ChatRoomListScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: ChatRoomListViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val searchState by viewModel.searchState.collectAsState()
        val selectedRoomIds by viewModel.selectedRoomIds.collectAsState()
        val unreadTotal by viewModel.unreadTotal.collectAsState()
        val chatGroups by viewModel.chatGroups.collectAsState()
        val groupsWithUnread by viewModel.groupsWithUnread.collectAsState()
        val isRefreshing by viewModel.isRefreshing.collectAsState()
        val rightNavigator = LocalRightNavigator.current
        val toastManager = LocalToastManager.current

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                handleChatRoomListEvent(event, toastManager)
            }
        }

        ChatRoomListContent(
            uiState = uiState,
            searchState = searchState,
            selectedRoomIds = selectedRoomIds,
            unreadTotal = unreadTotal,
            chatGroups = chatGroups,
            groupsWithUnread = groupsWithUnread,
            isRefreshing = isRefreshing,
            scrollToTop = viewModel.scrollToTop,
            newChatRoomPush = viewModel.newChatRoomPush,
            onAction = viewModel::onAction,
            onChatRoomClick = { item ->
                val current = rightNavigator.lastItem
                if (current is ChatRoomScreen && current.chatRoomId == item.id) return@ChatRoomListContent

                val newScreen = ChatRoomScreen(chatRoomId = item.id)
                if (current is EmptyScreen) {
                    rightNavigator.push(newScreen)
                } else {
                    rightNavigator.replace(newScreen)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomListContent(
    uiState: ChatRoomListUiState,
    searchState: Search.State,
    selectedRoomIds: Set<String>,
    unreadTotal: Int = 0,
    chatGroups: List<ChatGroup> = emptyList(),
    groupsWithUnread: Set<String> = emptySet(),
    isRefreshing: Boolean = false,
    scrollToTop: SharedFlow<Unit>? = null,
    onAction: (ChatRoomListActions) -> Unit,
    onChatRoomClick: (ChatRoom.Item) -> Unit = {},
    newChatRoomPush: SharedFlow<String>? = null,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val keyboardController = LocalSoftwareKeyboardController.current
    val showOverlay = LocalFullScreenOverlay.current

    // 필터/그룹 칩마다 스크롤 위치 보존 — 칩 전환 시 각 탭의 LazyListState 복원.
    val scrollKey = if (searchState.filterType == Search.FilterType.GROUP) {
        "group_${searchState.selectedGroupId}"
    } else {
        searchState.filterType.name
    }
    val listState = rememberSaveable(
        scrollKey,
        saver = LazyListState.Saver,
        key = "chatRoomListScroll_$scrollKey",
    ) { LazyListState() }

    // heightOffset 을 key 로 쓰면 스크롤 프레임마다 화면 스코프가 무효화되고 코루틴도 재기동된다.
    // snapshotFlow 로 구독을 이 이펙트 안에 가두고, 임계값을 넘는 순간만 걸러낸다.
    LaunchedEffect(scrollBehavior) {
        snapshotFlow { scrollBehavior.state.heightOffset < TOP_BAR_COLLAPSE_THRESHOLD }
            .distinctUntilChanged()
            .filter { it }
            .collect { keyboardController?.hide() }
    }

    /**
     * 검색어가 **바뀔 때만** 맨 위로. 첫 조립에서는 건너뛴다 — 탭을 다녀올 때마다 실행돼 복원된 스크롤을 0 으로
     * 되돌리던 것을 막는다.
     */
    var lastScrolledQuery by rememberSaveable { mutableStateOf(searchState.query) }
    LaunchedEffect(searchState.query) {
        if (searchState.query != lastScrolledQuery) {
            lastScrolledQuery = searchState.query
            listState.scrollToItem(0)
        }
    }

    if (scrollToTop != null) {
        LaunchedEffect(scrollToTop, listState) {
            scrollToTop.collect { listState.animateScrollToItem(0) }
        }
    }

    if (newChatRoomPush != null) {
        LaunchedEffect(newChatRoomPush, listState) {
            newChatRoomPush.collectLatest {
                if (listState.firstVisibleItemIndex < 3) {
                    delay(300)
                    listState.animateScrollToItem(0)
                }
            }
        }
    }

    ChatRoomListBackHandler(
        isEditMode = uiState.isEditMode,
        onExitEditMode = { onAction(ChatRoomListActions.SetMode(ChatRoomListMode.IDLE)) },
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBarV1(
                title = stringResource(Res.string.chatroom_list),
                scrollBehavior = scrollBehavior,
                actions = {
                    val showOverlay = LocalFullScreenOverlay.current

                    RefreshIconButton(
                        isRefreshing = isRefreshing,
                        onClick = { onAction(ChatRoomListActions.OnRefresh) },
                    )

                    Spacer(modifier = Modifier.size(10.dp))

                    EditModeIconButton(
                        isEditMode = uiState.isEditMode,
                        onToggle = {
                            val next = if (uiState.isEditMode) ChatRoomListMode.IDLE else ChatRoomListMode.EDIT
                            onAction(ChatRoomListActions.SetMode(next))
                        },
                    )

                    Spacer(modifier = Modifier.size(10.dp))

                    IconButton(
                        modifier = Modifier.size(28.dp),
                        onClick = { showOverlay(InviteScreen(InviteMode.CreateChatRoom)) }
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.chatroom_create_icon),
                            modifier = Modifier.size(28.dp),
                            contentDescription = "Create ChatRoom",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.size(15.dp))
                },
                bottomContent = {
                    SearchBar(
                        searchTxt = searchState.query,
                        hintTxt = stringResource(Res.string.chatroom_list_search_hint),
                        onSearchTextChanged = { onAction(ChatRoomListActions.OnSearchQueryChange(it)) },
                        onClearClick = { onAction(ChatRoomListActions.OnClearSearch) },
                        onSearchClick = { }
                    )

                    if (Config.ChatRoom.IS_CHAT_GROUP_ENABLED) {
                        val unreadChip = ChipItem(
                            text = stringResource(Res.string.unread),
                            isSelected = searchState.filterType == Search.FilterType.UNREAD,
                            onClick = { onAction(ChatRoomListActions.OnFilterTypeChange(Search.FilterType.UNREAD)) },
                            key = "__unread__",
                            badge = if (unreadTotal > 0) Res.drawable.new_circle else null
                        )
                        ChipRow(
                            items = buildList {
                                add(
                                    ChipItem(
                                        text = stringResource(Res.string.all),
                                        isSelected = searchState.filterType == Search.FilterType.ALL,
                                        onClick = { onAction(ChatRoomListActions.OnFilterTypeChange(Search.FilterType.ALL)) },
                                        key = "__all__"
                                    )
                                )
                                if (chatGroups.none { it.kind == "1" }) add(unreadChip)
                                chatGroups.forEach { group ->
                                    if (group.kind == "1") {
                                        add(unreadChip)
                                    } else {
                                        add(
                                            ChipItem(
                                                text = group.name,
                                                isSelected = searchState.filterType == Search.FilterType.GROUP &&
                                                        searchState.selectedGroupId == group.id,
                                                onClick = { onAction(ChatRoomListActions.OnGroupFilterChange(group.id)) },
                                                key = "group_${group.id}",
                                                badge = if (group.id in groupsWithUnread) Res.drawable.new_circle else null
                                            )
                                        )
                                    }
                                }
                                add(
                                    ChipItem(
                                        text = "",
                                        isSelected = false,
                                        onClick = { showOverlay(ChatRoomGroupManageScreen) },
                                        key = "__group_edit__",
                                        icon = Res.drawable.chatroom_group_icon,
                                        contentDescription = stringResource(Res.string.chat_group_edit),
                                        circular = true
                                    )
                                )
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val chatRoomItemMenu = rememberChatRoomItemMenu(onAction = onAction)

        val displayChatRooms = uiState.chatItems
        val pinnedRooms = remember(displayChatRooms) {
            displayChatRooms.filter { it.pinDate.isNotEmpty() }
        }
        val nonPinnedRooms = remember(displayChatRooms) {
            displayChatRooms.filter { it.pinDate.isEmpty() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Bg)
                .padding(paddingValues)
        ) {
            if (uiState is ChatRoomListUiState.Unread && displayChatRooms.isEmpty()) {
                UnreadEmptyState(modifier = Modifier.weight(1f))
            } else if (
                uiState is ChatRoomListUiState.Idle &&
                searchState.filterType == Search.FilterType.GROUP &&
                displayChatRooms.isEmpty()
            ) {
                GroupEmptyState(modifier = Modifier.weight(1f))
            } else {
                ChatRoomItemList(
                    pinnedRooms = pinnedRooms,
                    nonPinnedRooms = nonPinnedRooms,
                    listState = listState,
                    isEditMode = uiState.isEditMode,
                    onItemClick = onChatRoomClick,
                    onItemLongClick = {
                        chatRoomItemMenu.onLongClick(
                            item = it,
                            filterType = searchState.filterType,
                            selectedGroupId = searchState.selectedGroupId,
                            customGroups = chatGroups.filter { g -> g.kind == "2" }
                        )
                    },
                    onSelectRoom = { onAction(ChatRoomListActions.OnSelectRoom(it)) },
                    onLeave = { chatRoomItemMenu.onLeaveClick(it) },
                    modifier = Modifier.weight(1f),
                )
            }

            if (uiState.isEditMode) {
                LineDivider(modifier = Modifier.fillMaxWidth())
                ChatRoomEditBottomBar(
                    onLeave = { chatRoomItemMenu.onLeaveSelectedClick(hasSelection = selectedRoomIds.isNotEmpty()) }
                )
            }
        }
    }
}

@Composable
private fun ChatRoomListBackHandler(
    isEditMode: Boolean,
    onExitEditMode: () -> Unit,
) {
    val moveToBackground = rememberMoveToBackground()
    val rightPaneOpen = LocalRightPaneOpen.current
    BackHandler(enabled = isEditMode || !rightPaneOpen) {
        if (isEditMode) onExitEditMode()
        else moveToBackground()
    }
}

@Composable
private fun ChatRoomEditBottomBar(
    onLeave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.BgSub_2)
            .padding(10.dp)
    ) {
        ButtonV1(
            modifier = Modifier.weight(1f).height(46.dp),
            text = stringResource(Res.string.chat_rooom_out),
            containerColor = AppColors.Red,
            onClick = onLeave
        )
    }
}

/** 안읽음 탭에 표시할 대화방이 없을 때의 빈 상태. */
@Composable
private fun UnreadEmptyState(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(50.dp))

        Image(
            painter = painterResource(if (isDark) Res.drawable.empty_unread_dark else Res.drawable.empty_unread_light),
            contentDescription = null,
            modifier = Modifier.size(50.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.chatroom_unread_empty_title),
            color = AppColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(Res.string.chatroom_unread_empty_desc),
            color = AppColors.TextSub,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** 그룹 탭에 추가된 대화방이 없을 때의 빈 상태. */
@Composable
private fun GroupEmptyState(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(50.dp))

        Image(
            painter = painterResource(if (isDark) Res.drawable.empty_group_dark else Res.drawable.empty_group_light),
            contentDescription = null,
            modifier = Modifier.size(50.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.chatroom_group_empty_title),
            color = AppColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(Res.string.chatroom_group_empty_desc),
            color = AppColors.TextSub,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

private suspend fun handleChatRoomListEvent(
    event: ChatRoomListEvent,
    toastManager: ToastManager,
) {
    when (event) {
        is ChatRoomListEvent.ChatRoomRenamed -> {
            toastManager.show(
                if (event.success) getString(Res.string.toast_chat_room_renamed, event.newName)
                else getString(Res.string.toast_chat_room_rename_failed)
            )
        }
        is ChatRoomListEvent.RenameInvalid -> {
            val msg = when (event.reason) {
                ChatRoomNameValidation.Empty ->
                    getString(Res.string.chat_room_name_invalid_empty)
                ChatRoomNameValidation.TooLong ->
                    getString(Res.string.chat_room_name_invalid_too_long, CHAT_ROOM_NAME_MAX_LENGTH)
                ChatRoomNameValidation.Valid -> return
            }
            toastManager.show(msg)
        }
        is ChatRoomListEvent.ChatRoomAlarmChanged -> {
            val resId = when {
                !event.success -> Res.string.toast_chat_room_alarm_failed
                event.muted    -> Res.string.toast_chat_room_alarm_muted
                else           -> Res.string.toast_chat_room_alarm_unmuted
            }
            toastManager.show(getString(resId))
        }
        is ChatRoomListEvent.ChatRoomPinChanged -> {
            if (event.success) {
                val resId = if (event.pinned) Res.string.toast_chat_room_pinned
                            else Res.string.toast_chat_room_unpinned
                toastManager.show(getString(resId))
            }
        }
        is ChatRoomListEvent.ChatRoomLeft -> {
            val resId = if (event.success) Res.string.toast_chat_room_left
                        else Res.string.toast_chat_room_leave_failed
            toastManager.show(getString(resId))
        }
        is ChatRoomListEvent.ChatRoomsBulkLeft -> {
            toastManager.show(
                if (event.failed == 0) getString(Res.string.toast_chat_rooms_bulk_left, event.left)
                else getString(Res.string.toast_chat_rooms_bulk_partial, event.left, event.failed)
            )
        }
        is ChatRoomListEvent.ChatRoomAddedToGroup -> {
            toastManager.show(getString(Res.string.toast_chat_room_added_to_group, event.groupName))
        }
        is ChatRoomListEvent.ChatRoomRemovedFromGroup -> {
            toastManager.show(getString(Res.string.toast_chat_room_removed_from_group))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatRoomListScreenPreview() {
    MaterialTheme {
        ChatRoomListContent(
            uiState = ChatRoomListUiState.Idle(
                chatItems = listOf(
                    ChatRoom.Item()
                )
            ).copy(isEditMode = true),
            searchState = Search.State(),
            selectedRoomIds = emptySet(),
            onAction = {}
        )
    }
}

/** 상단바가 이만큼 접히면 스크롤 의도로 보고 키보드를 내린다. */
private const val TOP_BAR_COLLAPSE_THRESHOLD = -10f

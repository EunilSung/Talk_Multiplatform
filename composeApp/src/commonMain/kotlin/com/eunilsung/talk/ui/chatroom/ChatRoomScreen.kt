package com.eunilsung.talk.ui.chatroom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.eunilsung.talk.Config
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_down_icon
import multiplatformtalk.composeapp.generated.resources.arrow_up_icon
import multiplatformtalk.composeapp.generated.resources.emoticon_tab_face_human_down
import multiplatformtalk.composeapp.generated.resources.move_to_last_chat_icon
import multiplatformtalk.composeapp.generated.resources.search_date_icon
import multiplatformtalk.composeapp.generated.resources.search_user_icon
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.delete
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.data.local.RecentPhotosResult
import com.eunilsung.talk.data.remote.push.CurrentChatRoomTracker
import com.eunilsung.talk.data.remote.push.PushNotifier
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.chatroom.item.ChatRoomDrawerContent
import com.eunilsung.talk.ui.chatroom.item.ChatUserSelectPopup
import com.eunilsung.talk.ui.chatroom.item.FullTextDialog
import com.eunilsung.talk.ui.chatroom.input.MentionFieldState
import com.eunilsung.talk.ui.invite.InviteMode
import com.eunilsung.talk.ui.invite.InviteScreen
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.chatroom.vote.VoteMode
import com.eunilsung.talk.ui.chatroom.vote.VoteScreen
import com.eunilsung.talk.ui.main.LocalRightNavigator
import com.eunilsung.talk.ui.main.EmoticonOverlay
import com.eunilsung.talk.ui.main.emoticonPanelHeight
import com.eunilsung.talk.ui.main.rememberImeHeight
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.BackHandler
import com.eunilsung.talk.ui.uikit.datepicker.DatePicker
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import com.eunilsung.talk.ui.uikit.sheet.LocalBottomSheetManager
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import com.eunilsung.talk.ui.uikit.user.UserItemV2
import com.eunilsung.talk.ui.uikit.drawer.SideDrawer
import com.eunilsung.talk.ui.uikit.mediapicker.MediaPickerBindings
import com.eunilsung.talk.ui.uikit.mediapicker.MediaPickerHost
import com.eunilsung.talk.ui.uikit.mediapicker.rememberMediaPickerState
import com.eunilsung.talk.ui.userprofile.LocalShowUserProfile
import com.eunilsung.talk.util.chatDisplayText
import com.eunilsung.talk.util.splitLeadingToken
import org.jetbrains.compose.resources.painterResource
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.repository.ChatRoomListRepository
import kotlinx.coroutines.flow.map
import com.eunilsung.talk.domain.model.Bookmark
import com.eunilsung.talk.ui.uikit.emoticon.EmoticonPreview
import com.eunilsung.talk.ui.uikit.emoticon.StaticEmoticonImage
import com.eunilsung.talk.ui.uikit.emoticon.emoticonResourceNameForId
import com.eunilsung.talk.ui.uikit.emoticon.EmoticonItem
import com.eunilsung.talk.ui.uikit.emoticon.EmoticonPanelController
import com.eunilsung.talk.ui.uikit.emoticon.rememberStableImeHeight
import com.eunilsung.talk.ui.uikit.mediapicker.LocalShowFileDetail
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.mp.KoinPlatform

class ChatRoomScreen(
    val chatRoomId: String
) : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        DisposableEffect(chatRoomId) {
            CurrentChatRoomTracker.set(chatRoomId)

            runCatching {
                KoinPlatform.getKoin()
                    .get<PushNotifier>()
                    .cancel(chatRoomId)
            }

            onDispose {
                if (CurrentChatRoomTracker.currentChatRoomId == chatRoomId) {
                    CurrentChatRoomTracker.clear()
                }

                runCatching {
                    KoinPlatform.getKoin()
                        .get<PushNotifier>()
                        .cancel(chatRoomId)
                }

                runCatching {
                    KoinPlatform.getKoin()
                        .get<com.eunilsung.talk.domain.repository.ChatRoomRepository>()
                        .clearCurrentRoom(chatRoomId)
                }
            }
        }

        androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
            runCatching {
                KoinPlatform.getKoin()
                    .get<PushNotifier>()
                    .cancel(chatRoomId)
            }
        }

        val chatRoomListRepository: ChatRoomListRepository = koinInject()
        val viewModel: ChatRoomViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val users by viewModel.users.collectAsState()
        val senderOverride by viewModel.sender.collectAsState()
        val searchState by viewModel.searchState.collectAsState()
        val replyTarget by viewModel.replyTarget.collectAsState()
        val currentNotice by viewModel.currentNotice.collectAsState()
        val noticeBar by viewModel.noticeBar.collectAsState()
        val signals = remember(viewModel) {
            ChatRoomSignals(
                newChat = viewModel.newChatPush,
                mySend = viewModel.mySendPush,
                latestLoaded = viewModel.latestLoadedPush,
                entryScroll = viewModel.entryScroll,
            )
        }
        val noticeBindings = remember(viewModel, currentNotice, noticeBar) {
            NoticeBindings(
                notice = currentNotice,
                barState = noticeBar,
                onLoad = { viewModel.loadNoticeBarState(it) },
                onExpandedChange = { id, v -> viewModel.setNoticeExpanded(id, v) },
                onDetailsChange = { id, v -> viewModel.setNoticeDetailsShown(id, v) },
                onHide = { viewModel.hideNoticePermanently(it) },
            )
        }
        val chatSettings by viewModel.chatSettings.collectAsState()
        val recentPhotosResult by viewModel.recentPhotos.collectAsState()
        val galleryPhotos by viewModel.galleryPhotos.collectAsState()
        val galleryAlbums by viewModel.galleryAlbums.collectAsState()
        val selectedAlbumId by viewModel.selectedAlbumId.collectAsState()
        val rightNavigator = LocalRightNavigator.current

        val mediaPickerBindings = remember(
            viewModel,
            galleryPhotos,
            recentPhotosResult,
            galleryAlbums,
            selectedAlbumId,
        ) {
            MediaPickerBindings(
                recentPhotosFlow = viewModel.recentPhotos,
                galleryPhotos = galleryPhotos,
                galleryAlbums = galleryAlbums,
                selectedAlbumId = selectedAlbumId,
                onSelectGalleryAlbum = { viewModel.selectGalleryAlbum(it) },
                hasPhotoAccess = { viewModel.hasPhotoAccess() },
                onLoadRecentPhotos = { viewModel.loadRecentPhotos() },
                onLoadGalleryPhotos = { viewModel.loadGalleryPhotos() },
                onRequestPhotoAccess = { viewModel.requestPhotoAccess() },
                onLaunchFilePicker = { viewModel.launchFilePicker() },
                onSendPhotos = { ids ->
                    val recentList =
                        when (val r = recentPhotosResult) {
                            is RecentPhotosResult.Granted -> r.photos
                            is RecentPhotosResult.Limited -> r.photos
                            is RecentPhotosResult.Denied -> emptyList()
                        }
                    val byId = (recentList + galleryPhotos).associateBy { it.id }
                    val paths = ids.mapNotNull { byId[it]?.uri?.takeIf { uri -> uri.isNotBlank() } }
                    if (paths.isNotEmpty()) {
                        viewModel.onAction(ChatRoomActions.OnSendFiles(paths))
                    }
                },
                onCameraCapture = { path ->
                    if (path.isNotBlank()) {
                        viewModel.onAction(ChatRoomActions.OnSendFiles(listOf(path)))
                    }
                },
            )
        }

        val currentChatRoom: ChatRoom.Item? by remember(chatRoomId) {
            chatRoomListRepository.getChatRooms()
                .map { rooms -> rooms.firstOrNull { it.id == chatRoomId } }
        }.collectAsState(initial = null)

        LaunchedEffect(chatRoomId) {
            viewModel.onAction(ChatRoomActions.Load(chatRoomId))
        }

        LaunchedEffect(viewModel, chatRoomId) {
            viewModel.selfLeftPush.collect { leftRoomId ->
                if (leftRoomId == chatRoomId) {
                    rightNavigator.popUntilRoot()
                }
            }
        }

        ChatRoomContent(
            chatRoom = currentChatRoom,
            uiState = uiState,
            users = users,
            senderOverride = senderOverride,
            searchState = searchState,
            replyTarget = replyTarget,
            mediaPickerBindings = mediaPickerBindings,
            currentChatRoomId = chatRoomId,
            signals = signals,
            enterToSend = chatSettings.enterToSend,
            chatFontSize = chatSettings.fontSize.sp,
            noticeBindings = noticeBindings,
            bookmarksFlow = viewModel.bookmarks,
            initialEmoticonTab = remember { viewModel.lastEmoticonTab() },
            onEmoticonTabSelected = { viewModel.saveEmoticonTab(it) },
            onAction = { action ->
                when (action) {
                    is ChatRoomActions.OnClose -> {
                        if (searchState.isActive) viewModel.onAction(ChatRoomActions.OnExitSearch)
                        else rightNavigator.popUntilRoot()
                    }
                    else -> viewModel.onAction(action)
                }
            }

        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomContent(
    chatRoom: ChatRoom.Item?,
    uiState: ChatRoomUiState,
    users: List<User> = emptyList(),
    /** 전송 주체(샘플 전용). null 이면 나. */
    senderOverride: User? = null,
    searchState: ChatSearchState = ChatSearchState(),
    replyTarget: Chat.Item? = null,
    mediaPickerBindings: MediaPickerBindings = MediaPickerBindings.Preview,
    currentChatRoomId: String = "",
    signals: ChatRoomSignals = ChatRoomSignals(),
    enterToSend: Boolean = false,
    chatFontSize: TextUnit = 13.sp,
    noticeBindings: NoticeBindings = NoticeBindings(),
    bookmarksFlow: StateFlow<List<Bookmark>> = MutableStateFlow(emptyList()),
    initialEmoticonTab: Int = 0,
    onEmoticonTabSelected: (Int) -> Unit = {},
    initialSelectedEmoticon: EmoticonItem? = null,
    initialDrawerOpen: Boolean = false,
    onAction: (ChatRoomActions) -> Unit = {},
) {
    val bookmarks by bookmarksFlow.collectAsState()
    val bookmarkedChatIds = remember(bookmarks) { bookmarks.map { it.chatId }.toSet() }

    val fullScreenOverlay = LocalFullScreenOverlay.current
    val toastManager = LocalToastManager.current
    val bottomSheet = LocalBottomSheetManager.current
    val showUserProfileFromContent = LocalShowUserProfile.current
    val strings = rememberChatRoomStrings()

    val emoticon = remember { EmoticonPanelController() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var mentionDismissed by remember { mutableStateOf(false) }

    val hideKeyboard: () -> Unit = {
        keyboardController?.hide()
        emoticon.close()
        mentionDismissed = true
    }

    val chatInputFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    var lastEmoticonTab by remember { mutableStateOf(initialEmoticonTab) }

    var selectedEmoticon by remember { mutableStateOf(initialSelectedEmoticon) }

    val imeHeight = rememberImeHeight()
    val isKeyboardVisible = imeHeight > 0.dp
    val stableImeHeight by rememberStableImeHeight(imeHeight)

    // 패널이 열릴 높이 — 오버레이와 bottomBar 예약이 같은 값을 받아야 한다.
    val panelHeight = emoticonPanelHeight(stableImeHeight)

    // 키보드가 올라오면 이모티콘 패널을 내린다(둘이 하단을 동시에 차지하면 가린다).
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && emoticon.isOpen) emoticon.close()
    }

    var isDrawerOpen by remember { mutableStateOf(initialDrawerOpen) }
    // 공지 전문 다이얼로그에 띄울 본문 — null 이면 닫힘.
    var noticeDialogText by remember { mutableStateOf<String?>(null) }
    var isSearchUserBoxVisible by remember { mutableStateOf(false) }
    var isSenderBoxVisible by remember { mutableStateOf(false) }
    var isSearchDateBoxVisible by remember { mutableStateOf(false) }

    val mentionState = remember { MentionFieldState() }
    val mentionQuery: String? = mentionState.currentMentionQuery()
    LaunchedEffect(mentionQuery) { mentionDismissed = false }
    val mentionUsers: List<User> = remember(users, mentionQuery) {
        val q = mentionQuery?.trim().orEmpty()
        if (q.isEmpty()) users
        else users.filter { u ->
            u.name.contains(q, ignoreCase = true) ||
                u.departmentName?.contains(q, ignoreCase = true) == true ||
                u.positionName?.contains(q, ignoreCase = true) == true
        }
    }
    val showMentionBox = Config.ChatRoom.IS_MENTION_ENABLED &&
        !searchState.isActive && mentionQuery != null && !mentionDismissed && mentionUsers.isNotEmpty()
    val dialog = LocalDialogManager.current
    var longPressedChat by remember { mutableStateOf<Chat.Item?>(null) }
    val showFileDetail = LocalShowFileDetail.current
    val mediaPicker = rememberMediaPickerState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isSearchMode = searchState.isActive

    LaunchedEffect(replyTarget?.chatID) {
        if (replyTarget != null) {
            runCatching { chatInputFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }
    LaunchedEffect(isSearchMode) {
        if (isSearchMode) {
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    val listState = rememberLazyListState()
    val showMoveToLatestButton by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= 3 }
    }
    val isAtBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }

    var newChatBanner by remember { mutableStateOf<Chat.Item?>(null) }
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) newChatBanner = null
    }

    val latestChat = (uiState as? ChatRoomUiState.Success)?.groupedChats
        ?.firstOrNull { it.chat.chatStatue != Chat.Statue.SENDING && it.chat.chatStatue != Chat.Statue.FAIL }?.chat
    val latestChatState = androidx.compose.runtime.rememberUpdatedState(latestChat)
    val newChatPush = signals.newChat
    if (newChatPush != null) {
        LaunchedEffect(newChatPush, currentChatRoomId) {
            newChatPush.collect { roomId ->
                if (roomId != currentChatRoomId) return@collect
                delay(50)
                val chat = latestChatState.value ?: return@collect
                if (listState.firstVisibleItemIndex <= 1) {
                    newChatBanner = null
                    listState.animateScrollToItem(0)
                } else if (!chat.isMe) {
                    newChatBanner = chat
                }
            }
        }
    }

    LaunchedEffect(isSearchMode) {
        if (!isSearchMode) {
            isSearchUserBoxVisible = false
            isSearchDateBoxVisible = false
        }
    }

    val handleBack: () -> Unit = {
        when {
            showMentionBox -> mentionDismissed = true
            selectedEmoticon != null -> selectedEmoticon = null
            emoticon.isOpen && emoticon.isExpanded -> emoticon.collapse()
            emoticon.isOpen -> emoticon.close()
            isSenderBoxVisible -> isSenderBoxVisible = false
            isSearchUserBoxVisible -> isSearchUserBoxVisible = false
            isSearchDateBoxVisible -> isSearchDateBoxVisible = false
            isDrawerOpen -> isDrawerOpen = false
            replyTarget != null -> onAction(ChatRoomActions.OnCancelReply)

            else -> onAction(ChatRoomActions.OnClose)
        }
    }

    BackHandler(enabled = true, onBack = handleBack)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier,
            topBar = {
                ChatRoomTopBar(
                    chatRoom = chatRoom,
                    isSearchMode = isSearchMode,
                    searchState = searchState,
                    senderOverride = senderOverride,
                    scrollBehavior = scrollBehavior,
                    searchFocusRequester = searchFocusRequester,
                    hideKeyboard = hideKeyboard,
                    handleBack = handleBack,
                    isDrawerOpen = isDrawerOpen,
                    bookmarksFlow = bookmarksFlow,
                    onSenderPick = { isSenderBoxVisible = true },
                    onDrawerOpen = { isDrawerOpen = true },
                    onAction = onAction,
                )
            },
            bottomBar = {
                ChatRoomBottomBar(
                    isSearchMode = isSearchMode,
                    searchState = searchState,
                    replyTarget = replyTarget,
                    mentionState = mentionState,
                    emoticon = emoticon,
                    selectedEmoticon = selectedEmoticon,
                    onSelectedEmoticonChange = { selectedEmoticon = it },
                    lastEmoticonTab = lastEmoticonTab,
                    onEmoticonTabChange = { lastEmoticonTab = it },
                    onEmoticonTabSelected = onEmoticonTabSelected,
                    enterToSend = enterToSend,
                    isKeyboardVisible = isKeyboardVisible,
                    imeHeight = imeHeight,
                    panelHeight = panelHeight,
                    mediaPicker = mediaPicker,
                    chatInputFocusRequester = chatInputFocusRequester,
                    chatFontSize = chatFontSize,
                    isSearchUserBoxVisible = isSearchUserBoxVisible,
                    onSearchUserBoxToggle = {
                        isSearchUserBoxVisible = !isSearchUserBoxVisible
                        if (isSearchUserBoxVisible) isSearchDateBoxVisible = false
                    },
                    isSearchDateBoxVisible = isSearchDateBoxVisible,
                    onSearchDateBoxToggle = {
                        isSearchDateBoxVisible = !isSearchDateBoxVisible
                        if (isSearchDateBoxVisible) isSearchUserBoxVisible = false
                    },
                    hideKeyboard = hideKeyboard,
                    onAction = onAction,
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.ChatRoomBg)
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding()
                    )
            ) {
                when (uiState) {
                    is ChatRoomUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is ChatRoomUiState.Error -> {}
                    is ChatRoomUiState.Success -> {
                        if (uiState.groupedChats.isNotEmpty()) {
                            ChatScrollController(
                                listState = listState,
                                groupedChats = uiState.groupedChats,
                                currentChatRoomId = currentChatRoomId,
                                searchState = searchState,
                                signals = signals,
                                onAction = onAction,
                            )
                            var lastShakenFocusKey by remember { mutableStateOf(-1) }

                            ChatMessageList(
                                groupedChats = uiState.groupedChats,
                                listState = listState,
                                searchState = searchState,
                                chatFontSize = chatFontSize,
                                bookmarkedChatIds = bookmarkedChatIds,
                                currentChatRoomId = currentChatRoomId,
                                strings = strings,
                                lastShakenFocusKey = lastShakenFocusKey,
                                onShakenFocusKeyChange = { lastShakenFocusKey = it },
                                mediaPicker = mediaPicker,
                                hideKeyboard = hideKeyboard,
                                users = users,
                                onLongPress = { longPressedChat = it },
                                // 지금 걸린 공지가 아니라 그 대화가 담고 있던 본문을 보여준다.
                                onNoticeClick = { noticeDialogText = it.chatContent },
                                onAction = onAction,
                            )
                        }

                        if (newChatBanner != null) {
                            val banner = newChatBanner!!
                            // 이모티콘 id 토큰을 떼어내 이미지로 그린다.
                            val bannerPreview by produceState("", banner) {
                                value = chatDisplayText(banner)
                            }
                            val (bannerEmoticon, bannerText) = remember(bannerPreview) {
                                val trimmed = bannerPreview.trim()
                                val (token, rest) = splitLeadingToken(trimmed)
                                val resource = token?.let { emoticonResourceNameForId(it) }
                                if (resource != null) resource to rest else null to trimmed
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppColors.BgSub)
                                    .border(1.dp, AppColors.Line, RoundedCornerShape(12.dp)),
                            ) {
                                UserItemV2(
                                    userId = banner.user.id,
                                    name = banner.user.name,
                                    content = bannerText,
                                    leadingContent = bannerEmoticon?.let { resource ->
                                        {
                                            StaticEmoticonImage(
                                                resourceName = resource,
                                                contentDescription = null,
                                                modifier = Modifier.height(18.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        onAction(ChatRoomActions.OnMoveToLatest)
                                        newChatBanner = null
                                    },
                                )
                            }
                        }

                        if (showMoveToLatestButton && newChatBanner == null) {
                            IconButton(
                                onClick = { onAction(ChatRoomActions.OnMoveToLatest) },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 8.dp),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.move_to_last_chat_icon),
                                    contentDescription = "Move To Last Chat",
                                    tint = Color.Unspecified
                                )
                            }
                        }

                        if (isSenderBoxVisible) {
                            ChatUserSelectPopup(
                                users = users,
                                selectedUserId = senderOverride?.id ?: Config.MyInfo.userId,
                                onDismiss = { isSenderBoxVisible = false },
                                onUserClick = {
                                    onAction(ChatRoomActions.OnSelectSender(it))
                                    isSenderBoxVisible = false
                                },
                            )
                        }

                        if (isSearchMode && isSearchUserBoxVisible) {
                            ChatUserSelectPopup(
                                users = users,
                                onDismiss = { isSearchUserBoxVisible = false },
                                onUserClick = {
                                    onAction(ChatRoomActions.OnSearchUserSelect(it))
                                    isSearchUserBoxVisible = false
                                },
                            )
                        }

                        if (showMentionBox) {
                            ChatUserSelectPopup(
                                users = mentionUsers,
                                onDismiss = { mentionDismissed = true },
                                onUserClick = { mentionState.insertMention(it.id, it.name) },
                            )
                        }

                    }
                }

                val notice = noticeBindings.notice
                if (notice != null) {
                    val id = notice.identityKey
                    LaunchedEffect(id) { noticeBindings.onLoad(id) }

                    if (!noticeBindings.barState.hidden) {
                        NoticeBarCard(
                            notice = notice,
                            expanded = noticeBindings.barState.expanded,
                            showDetails = noticeBindings.barState.showDetails,
                            modifier = Modifier.align(Alignment.TopCenter),
                            onShowDetailsChange = { v -> noticeBindings.onDetailsChange(id, v) },
                            onToggle = { noticeBindings.onExpandedChange(id, !noticeBindings.barState.expanded) },
                            onHideToFab = { noticeBindings.onExpandedChange(id, false) },
                            onHidePermanently = { noticeBindings.onHide(id) },
                            onDelete = {
                                dialog.confirm(
                                    title = strings.noticeDeleteTitle,
                                    message = strings.noticeDeleteMessage,
                                    confirmText = strings.delete,
                                    dismissText = strings.cancel,
                                    onConfirm = { onAction(ChatRoomActions.OnDeleteNotice) },
                                )
                            },
                        )
                    }
                }

                EmoticonPreview(
                    previewEmoticon = selectedEmoticon,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onDismiss = { selectedEmoticon = null }
                )
            }
        }

        if (isSearchMode && isSearchDateBoxVisible && uiState is ChatRoomUiState.Success) {
            val availableDates = remember(uiState.groupedChats) {
                uiState.groupedChats
                    .filter { it.showDate }
                    .map { it.chat.date.take(10) }
                    .distinct()
            }
            DatePicker(
                availableDates = availableDates,
                onDateSelected = { datePrefix ->
                    onAction(ChatRoomActions.OnSearchByDate(datePrefix))
                    isSearchDateBoxVisible = false
                },
                onDismiss = { isSearchDateBoxVisible = false }
            )
        }

        SideDrawer(
            isVisible = isDrawerOpen,
            onDismiss = { isDrawerOpen = false }
        ) {
            val showUserProfile = LocalShowUserProfile.current
            ChatRoomDrawerContent(
                users = users,
                onVoteClick = {
                    isDrawerOpen = false
                    fullScreenOverlay(
                        VoteScreen(
                            chatRoomId = currentChatRoomId,
                            initialMode = VoteMode.LIST,
                        )
                    )
                },
                onNoticeClick = {
                    isDrawerOpen = false
                    val content = noticeBindings.notice?.content
                    if (content?.isNotBlank() == true) {
                        noticeDialogText = content
                    } else {
                        toastManager.show(strings.noNotice)
                    }
                },
                onInviteClick = {
                    isDrawerOpen = false
                    fullScreenOverlay(
                        InviteScreen(
                            InviteMode.InviteToChatRoom(
                                chatRoomId = currentChatRoomId,
                                existingUserIds = users.map { it.id },
                                createNewRoom = chatRoom?.isDirectRoom == true,
                                existingUsers = users.map { it.id to it.name },
                            )
                        )
                    )
                },
                onUserClick = { showUserProfile(it) },
            )
        }

        noticeDialogText?.takeIf { it.isNotBlank() }?.let { content ->
            FullTextDialog(
                text = content,
                linkColor = AppColors.SkyLine,
                onDismiss = { noticeDialogText = null },
            )
        }

        EmoticonOverlay(
            emoticon = emoticon,
            panelHeight = panelHeight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        MediaPickerHost(
            state = mediaPicker,
            bindings = mediaPickerBindings,
        )

        ChatActionMenu(
            target = longPressedChat,
            isBookmarked = longPressedChat?.let { bookmarkedChatIds.contains(it.chatID) } == true,
            strings = strings,
            onDismiss = { longPressedChat = null },
            onAction = onAction,
        )
    }
}

@Composable
internal fun ChatSearchNav(
    searchState: ChatSearchState,
    onUserClick: () -> Unit = {},
    onDateClick: () -> Unit = {},
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val total = searchState.totalMatches
        val pos = searchState.currentPosition

        if (Config.ChatRoom.IS_SEARCH_USER_ENABLED) {
            IconButton(
                modifier = Modifier.size(30.dp),
                onClick = onUserClick,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.search_user_icon),
                    modifier = Modifier.size(24.dp),
                    contentDescription = "Search user",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        if (Config.ChatRoom.IS_SEARCH_DATE_ENABLED) {
            IconButton(
                modifier = Modifier.size(30.dp),
                onClick = onDateClick,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.search_date_icon),
                    modifier = Modifier.size(24.dp),
                    contentDescription = "Search date",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = if (total == 0) "0" else "$pos / $total",
            color = AppColors.Text,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.width(20.dp))

        IconButton(
            modifier = Modifier.size(24.dp),
            onClick = onPrevClick,
            enabled = total > 0 && searchState.currentIndex > 0
        ) {
            Icon(
                painter = painterResource(Res.drawable.arrow_up_icon),
                modifier = Modifier.size(24.dp),
                contentDescription = "Prev match",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        IconButton(
            modifier = Modifier.size(24.dp),
            onClick = onNextClick,
            enabled = total > 0 && searchState.currentIndex < total - 1
        ) {
            Icon(
                painter = painterResource(Res.drawable.arrow_down_icon),
                modifier = Modifier.size(24.dp),
                contentDescription = "Next match",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatRoomScreenPreview() {
    MaterialTheme {
        ChatRoomContent(
            chatRoom = ChatRoom.Item(),
            uiState = ChatRoomUiState.Success(
                groupedChats = emptyList(),
            ),
            onAction = {}
        )
    }
}

private val previewUsers = listOf(
    User(id = "1", name = "성은일", departmentName = "개발팀", positionName = "팀장"),
    User(id = "2", name = "홍길동", departmentName = "기획팀"),
    User(id = "3", name = "김철수"),
)

@Preview(showBackground = true)
@Composable
private fun ChatRoomSearchModePreview() {
    MaterialTheme {
        ChatRoomContent(
            chatRoom = ChatRoom.Item(),
            uiState = ChatRoomUiState.Success(groupedChats = emptyList()),
            users = previewUsers,
            searchState = ChatSearchState(
                isActive = true,
                query = "검색어",
                matchedChatIds = listOf("a", "b", "c", "d", "e"),
                currentIndex = 1,
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatRoomReplyModePreview() {
    MaterialTheme {
        ChatRoomContent(
            chatRoom = ChatRoom.Item(),
            uiState = ChatRoomUiState.Success(groupedChats = emptyList()),
            users = previewUsers,
            replyTarget = Chat.Item(
                user = User(name = "성은일"),
                chatContent = "답장 대상 본문입니다.",
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatRoomEmoticonPreviewPreview() {
    MaterialTheme {
        ChatRoomContent(
            chatRoom = ChatRoom.Item(),
            uiState = ChatRoomUiState.Success(groupedChats = emptyList()),
            users = previewUsers,
            initialSelectedEmoticon = EmoticonItem(
                id = "sample",
                resource = Res.drawable.emoticon_tab_face_human_down,
                resourcePath = "drawable/emoticon_tab_face_human_down.png",
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatRoomDrawerPreview() {
    MaterialTheme {
        ChatRoomContent(
            chatRoom = ChatRoom.Item(),
            uiState = ChatRoomUiState.Success(groupedChats = emptyList()),
            users = previewUsers,
            initialDrawerOpen = true,
            onAction = {},
        )
    }
}


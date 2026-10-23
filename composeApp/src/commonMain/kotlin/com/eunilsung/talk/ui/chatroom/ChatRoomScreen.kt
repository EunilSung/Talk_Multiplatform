package com.eunilsung.talk.ui.chatroom

import com.eunilsung.talk.ui.share.ShareScreen
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.main.popToEmptyRoot
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.eunilsung.talk.Config
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.move_to_last_chat_icon
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.delete
import kotlinx.coroutines.delay
import com.eunilsung.talk.data.local.RecentPhotosResult
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.chatroom.item.ChatUserSelectPopup
import com.eunilsung.talk.ui.chatroom.item.FullTextDialog
import com.eunilsung.talk.ui.chatroom.input.MentionFieldState
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
import com.eunilsung.talk.ui.uikit.mediapicker.MediaPickerBindings
import com.eunilsung.talk.ui.uikit.TestTags
import com.eunilsung.talk.ui.uikit.mediapicker.MediaPickerHost
import com.eunilsung.talk.ui.uikit.mediapicker.rememberMediaPickerState
import com.eunilsung.talk.ui.userprofile.LocalShowUserProfile
import org.jetbrains.compose.resources.painterResource
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.ui.uikit.emoticon.EmoticonPreview
import com.eunilsung.talk.ui.uikit.emoticon.EmoticonItem
import com.eunilsung.talk.ui.uikit.emoticon.EmoticonPanelController
import com.eunilsung.talk.ui.uikit.emoticon.rememberStableImeHeight
import com.eunilsung.talk.ui.uikit.mediapicker.LocalShowFileDetail
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class ChatRoomScreen(
    val chatRoomId: String
) : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: ChatRoomViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val rightNavigator = LocalRightNavigator.current
        val toastManager = LocalToastManager.current
        val strings = rememberChatRoomStrings()

        DisposableEffect(chatRoomId) {
            viewModel.onAction(ChatRoomActions.OnEnterRoom(chatRoomId))
            onDispose { viewModel.onAction(ChatRoomActions.OnLeaveRoom(chatRoomId)) }
        }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.onAction(ChatRoomActions.OnResumeRoom(chatRoomId))
        }

        LaunchedEffect(chatRoomId) {
            viewModel.onAction(ChatRoomActions.Load(chatRoomId))
        }

        LaunchedEffect(viewModel, chatRoomId) {
            viewModel.events.collect { event ->
                when (event) {
                    ChatRoomEvent.SelfLeft -> rightNavigator.popToEmptyRoot()
                    ChatRoomEvent.TranslationFailed -> toastManager.show(strings.translateUnavailable)
                    ChatRoomEvent.AiSummaryFileNotReady ->
                        toastManager.show(strings.aiSummaryFileNotReady, testTag = TestTags.TOAST_AI_SUMMARY_FILE_NOT_READY)
                    ChatRoomEvent.AiAgentNotInstalled ->
                        toastManager.show(strings.aiAgentNotInstalled, testTag = TestTags.TOAST_AI_AGENT_NOT_INSTALLED)
                }
            }
        }

        val media = uiState.media
        val mediaPickerBindings = remember(viewModel, media) {
            MediaPickerBindings(
                recentPhotos = media.recentPhotos,
                galleryPhotos = media.galleryPhotos,
                galleryAlbums = media.galleryAlbums,
                selectedAlbumId = media.selectedAlbumId,
                onSelectGalleryAlbum = { viewModel.onAction(ChatRoomActions.OnSelectGalleryAlbum(it)) },
                hasPhotoAccess = media.hasPhotoAccess,
                onLoadRecentPhotos = { viewModel.onAction(ChatRoomActions.OnLoadRecentPhotos) },
                onLoadGalleryPhotos = { viewModel.onAction(ChatRoomActions.OnLoadGalleryPhotos) },
                onRequestPhotoAccess = { viewModel.onAction(ChatRoomActions.OnRequestPhotoAccess) },
                onLaunchFilePicker = { viewModel.onAction(ChatRoomActions.OnLaunchFilePicker) },
                onSendPhotos = { ids ->
                    val recentList =
                        when (val r = media.recentPhotos) {
                            is RecentPhotosResult.Granted -> r.photos
                            is RecentPhotosResult.Limited -> r.photos
                            is RecentPhotosResult.Denied -> emptyList()
                        }
                    val byId = (recentList + media.galleryPhotos).associateBy { it.id }
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

        ChatRoomContent(
            uiState = uiState,
            mediaPickerBindings = mediaPickerBindings,
            currentChatRoomId = chatRoomId,
            signals = viewModel.signals,
            onAction = { action ->
                when (action) {
                    is ChatRoomActions.OnClose -> {
                        if (uiState.search.isActive) viewModel.onAction(ChatRoomActions.OnExitSearch)
                        else rightNavigator.popToEmptyRoot()
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
    uiState: ChatRoomUiState,
    mediaPickerBindings: MediaPickerBindings = MediaPickerBindings.Preview,
    currentChatRoomId: String = "",
    signals: ChatRoomSignals = ChatRoomSignals(),
    initialSelectedEmoticon: EmoticonItem? = null,
    initialDrawerOpen: Boolean = false,
    onAction: (ChatRoomActions) -> Unit = {},
) {
    val chatRoom = uiState.chatRoom
    val messages = uiState.messages
    val users = uiState.users
    val senderOverride = uiState.sender
    val myId = uiState.myId
    val searchState = uiState.search
    val replyTarget = uiState.replyTarget
    val translations = uiState.translations
    val polish = uiState.polish
    val enterToSend = uiState.chatSettings.enterToSend
    val chatFontSize = uiState.chatSettings.fontSize.sp
    val notice = uiState.notice
    val noticeBar = uiState.noticeBar
    val onEmoticonTabSelected: (Int) -> Unit = { onAction(ChatRoomActions.OnEmoticonTabSelected(it)) }

    val bookmarks = uiState.bookmarks
    val bookmarkedChatIds = remember(bookmarks) { bookmarks.map { it.chatId }.toSet() }
    val bookmarksState = rememberUpdatedState(bookmarks)

    val toastManager = LocalToastManager.current
    val bottomSheet = LocalBottomSheetManager.current
    val fullScreenOverlay = LocalFullScreenOverlay.current
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
    var lastEmoticonTab by remember(uiState.initialEmoticonTab) { mutableStateOf(uiState.initialEmoticonTab) }

    var selectedEmoticon by remember { mutableStateOf(initialSelectedEmoticon) }

    val imeHeight = rememberImeHeight()
    val isKeyboardVisible = imeHeight > 0.dp
    val stableImeHeight by rememberStableImeHeight(imeHeight)

    /** 패널이 열릴 높이 — 오버레이와 bottomBar 예약이 같은 값을 받아야 한다. */
    val panelHeight = emoticonPanelHeight(stableImeHeight)

    /** 키보드가 올라오면 이모티콘 패널을 내린다(둘이 하단을 동시에 차지하면 가린다). */
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && emoticon.isOpen) emoticon.close()
    }

    var isDrawerOpen by remember { mutableStateOf(initialDrawerOpen) }
    /** 공지 전문 다이얼로그에 띄울 본문 — null 이면 닫힘. */
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

    val polishInput = rememberPolishInputState(mentionState)
    LaunchedEffect(polish) {
        when (polish) {
            is PolishUiState.Ready -> {
                polishInput.apply(polish)
                onAction(ChatRoomActions.OnPolishHandled)
            }
            PolishUiState.Failed -> {
                toastManager.show(strings.polishUnavailable)
                onAction(ChatRoomActions.OnPolishHandled)
            }
            else -> Unit
        }
    }
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

    val latestChat = (messages as? ChatMessagesUiState.Success)?.groupedChats
        ?.firstOrNull { it.chat.chatStatus != Chat.Status.SENDING && it.chat.chatStatus != Chat.Status.FAIL }?.chat
    val latestChatState = rememberUpdatedState(latestChat)
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
                    bookmarks = { bookmarksState.value },
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
                    polishButton = polishInput.buttonState(polish),
                    onPolishClick = polishInput::onButtonClick,
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
                when (messages) {
                    is ChatMessagesUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is ChatMessagesUiState.Error -> {}
                    is ChatMessagesUiState.Success -> {
                        if (messages.groupedChats.isNotEmpty()) {
                            ChatScrollController(
                                listState = listState,
                                groupedChats = messages.groupedChats,
                                currentChatRoomId = currentChatRoomId,
                                searchState = searchState,
                                signals = signals,
                                onAction = onAction,
                            )
                            var lastShakenFocusKey by remember { mutableStateOf(-1) }

                            ChatMessageList(
                                groupedChats = messages.groupedChats,
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
                                translations = translations,
                                /** 지금 걸린 공지가 아니라 그 대화가 담고 있던 본문을 보여준다. */
                                onNoticeClick = { noticeDialogText = it.chatContent },
                                onAction = onAction,
                            )
                        }

                        newChatBanner?.let { banner ->
                            NewChatBanner(
                                chat = banner,
                                onClick = {
                                    onAction(ChatRoomActions.OnMoveToLatest)
                                    newChatBanner = null
                                },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
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
                                selectedUserId = senderOverride?.id ?: myId,
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

                if (notice != null) {
                    val id = notice.identityKey
                    LaunchedEffect(id) { onAction(ChatRoomActions.OnLoadNoticeBar(id)) }

                    if (!noticeBar.hidden) {
                        NoticeBarCard(
                            notice = notice,
                            expanded = noticeBar.expanded,
                            showDetails = noticeBar.showDetails,
                            modifier = Modifier.align(Alignment.TopCenter),
                            onShowDetailsChange = { v -> onAction(ChatRoomActions.OnNoticeDetailsChange(id, v)) },
                            onToggle = { onAction(ChatRoomActions.OnNoticeExpandedChange(id, !noticeBar.expanded)) },
                            onHideToFab = { onAction(ChatRoomActions.OnNoticeExpandedChange(id, false)) },
                            onHidePermanently = { onAction(ChatRoomActions.OnHideNotice(id)) },
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

        if (isSearchMode && isSearchDateBoxVisible && messages is ChatMessagesUiState.Success) {
            val availableDates = remember(messages.groupedChats) {
                messages.groupedChats
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

        ChatRoomDrawer(
            isOpen = isDrawerOpen,
            chatRoom = chatRoom,
            currentChatRoomId = currentChatRoomId,
            users = users,
            myId = myId,
            notice = notice,
            strings = strings,
            onDismiss = { isDrawerOpen = false },
            onShowNotice = { noticeDialogText = it },
        )

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

        if (polishInput.isMenuOpen) {
            PolishStyleMenu(
                strings = strings,
                onDismiss = { polishInput.isMenuOpen = false },
                onStyleSelected = { style ->
                    polishInput.isMenuOpen = false
                    onAction(ChatRoomActions.OnPolishText(mentionState.text, style))
                },
            )
        }

        ChatActionMenu(
            target = longPressedChat,
            isBookmarked = longPressedChat?.let { bookmarkedChatIds.contains(it.chatID) } == true,
            isTranslated = longPressedChat?.let { translations.containsKey(it.chatID) } == true,
            strings = strings,
            onDismiss = { longPressedChat = null },
            onForward = { chat ->
                fullScreenOverlay(ShareScreen(forwardChat = chat, forwardSourceRoomId = currentChatRoomId))
            },
            onAction = onAction,
        )
    }
}

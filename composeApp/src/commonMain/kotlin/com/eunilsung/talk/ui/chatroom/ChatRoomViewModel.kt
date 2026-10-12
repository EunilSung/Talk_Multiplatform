package com.eunilsung.talk.ui.chatroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eunilsung.talk.data.local.FilePickerProvider
import com.eunilsung.talk.data.local.RecentPhotosProvider
import com.eunilsung.talk.data.local.RecentPhotosResult
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.PolishStyle
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases
import com.eunilsung.talk.domain.usecase.ChatRoomUseCases
import com.eunilsung.talk.util.Log
import com.eunilsung.talk.util.chatDisplayText
import com.eunilsung.talk.data.local.FilePickerRequestId
import com.eunilsung.talk.domain.usecase.InviteUseCases
import com.eunilsung.talk.domain.usecase.ChatRoomStateUseCases

class ChatRoomViewModel(
    private val chatRoomUseCases: ChatRoomUseCases,
    private val stateUseCases: ChatRoomStateUseCases,
    private val recentPhotosProvider: RecentPhotosProvider,
    private val filePickerProvider: FilePickerProvider,
    private val chatRoomListUseCases: ChatRoomListUseCases,
    private val inviteUseCases: InviteUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatRoomUiState(
            chatSettings = stateUseCases.observeChatSettings().value,
            initialEmoticonTab = stateUseCases.getLastEmoticonTab(),
            media = ChatMediaUiState(hasPhotoAccess = recentPhotosProvider.hasPhotoAccess()),
        )
    )
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    private val _entryScroll = MutableSharedFlow<String?>(replay = 1, extraBufferCapacity = 1)

    /** 저장소가 밀어 주는 일회성 신호 — 대화 목록의 스크롤과 새 대화 배너가 쓴다. */
    val signals: ChatRoomSignals = ChatRoomSignals(
        newChat = stateUseCases.observeNewChat(),
        mySend = stateUseCases.observeMySend(),
        latestLoaded = stateUseCases.observeLatestLoaded(),
        entryScroll = _entryScroll,
    )

    private val _events = Channel<ChatRoomEvent>(Channel.BUFFERED)
    val events: Flow<ChatRoomEvent> = _events.receiveAsFlow()

    /** picker 결과가 싱글톤 provider 로 broadcast 되므로, 이 VM 이 띄운 요청만 소비하기 위한 태그. */
    private val filePickerRequestId =
        FilePickerRequestId.next("chatroom")

    init {
        viewModelScope.launch {
            filePickerProvider.results.collect { files ->
                if (files.isEmpty()) return@collect
                if (filePickerProvider.lastRequestId != filePickerRequestId) return@collect
                Log.message(
                    "[FilePicker] received ${files.size} files: " +
                            files.joinToString { "${it.name}(${it.sizeBytes})" }
                )
                onAction(ChatRoomActions.OnSendFiles(files.map { it.uri }))
            }
        }
        viewModelScope.launch {
            stateUseCases.observeChatSettings().collect { settings ->
                _uiState.update { it.copy(chatSettings = settings) }
            }
        }
        viewModelScope.launch {
            stateUseCases.observeCurrentNotice().collect { notice ->
                _uiState.update { it.copy(notice = notice) }
            }
        }
        viewModelScope.launch {
            stateUseCases.observeBookmarks().collect { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }
        viewModelScope.launch {
            stateUseCases.observeSelfLeft()
                .filter { it == currentChatRoomId }
                .collect { _events.send(ChatRoomEvent.SelfLeft) }
        }
    }

    private fun launchFilePicker() {
        filePickerProvider.launchFilePicker(
            requestId = filePickerRequestId,
            allowMultiple = true,
            mimeTypes = listOf("*/*"),
        )
    }

    private var galleryJob: Job? = null

    private fun updateMedia(transform: (ChatMediaUiState) -> ChatMediaUiState) {
        _uiState.update { it.copy(media = transform(it.media)) }
    }

    /** 앨범 선택 — 해당 폴더의 사진만 다시 읽는다. */
    private fun selectGalleryAlbum(albumId: String?) {
        updateMedia { it.copy(selectedAlbumId = albumId, galleryPhotos = emptyList()) }
        loadGalleryPhotos()
    }

    /**
     * 갤러리 사진 로딩.
     *
     * 이전 로딩은 반드시 취소하고, 결과를 쓰기 전에 요청 당시 앨범이 아직 선택 상태인지 확인한다.
     * iOS 는 썸네일을 디코드하며 onPartial 로 중간 결과를 계속 흘리므로, 앨범을 빠르게 오가면
     * 이전 앨범의 늦은 콜백이 새 목록을 덮어써 엉뚱한 사진이 보인다.
     * (취소는 즉시 전파되지 않아 이미 출발한 콜백이 남을 수 있어 확인이 따로 필요하다.)
     */
    private fun loadGalleryPhotos(limit: Int = 500) {
        val albumId = _uiState.value.media.selectedAlbumId
        updateMedia { it.copy(hasPhotoAccess = recentPhotosProvider.hasPhotoAccess()) }
        galleryJob?.cancel()
        galleryJob = viewModelScope.launch {
            fun apply(r: RecentPhotosResult) {
                if (_uiState.value.media.selectedAlbumId != albumId) return
                val photos = when (r) {
                    is RecentPhotosResult.Granted -> r.photos
                    is RecentPhotosResult.Limited -> r.photos
                    is RecentPhotosResult.Denied -> emptyList()
                }
                updateMedia { it.copy(galleryPhotos = photos) }
            }
            val result = runCatching {
                recentPhotosProvider.fetchByAlbum(
                    albumId = albumId,
                    limit = limit,
                    onPartial = ::apply,
                )
            }.getOrElse { RecentPhotosResult.Granted(emptyList()) }
            if (isActive) apply(result)
        }
        loadGalleryAlbums()
    }

    /**
     * 앨범 목록 조회 — 시트를 열 때 한 번만.
     *
     * 사진 로딩과 별도 코루틴으로 돌린다. iOS 는 사진 500장의 썸네일을 전부 디코드한 뒤에야
     * 조회가 끝나므로, 뒤에 붙이면 필터 버튼이 한참 뒤에야 나타난다.
     */
    private fun loadGalleryAlbums() {
        if (_uiState.value.media.galleryAlbums.isNotEmpty()) return
        viewModelScope.launch {
            val albums = runCatching { recentPhotosProvider.fetchAlbums() }
                .getOrElse { emptyList() }
            updateMedia { it.copy(galleryAlbums = albums) }
        }
    }

    /** 공지가 바뀔 때 저장된 표시 상태를 읽어 온다. */
    private fun loadNoticeBarState(identityKey: String) {
        val saved = stateUseCases.getNoticeBarState(identityKey)
        _uiState.update {
            it.copy(
                noticeBar = NoticeBarUiState(
                    hidden = saved.hidden,
                    expanded = !saved.collapsed,
                    showDetails = saved.detailsShown,
                )
            )
        }
    }

    private fun updateNoticeBar(transform: (NoticeBarUiState) -> NoticeBarUiState) {
        _uiState.update { it.copy(noticeBar = transform(it.noticeBar)) }
    }

    private fun loadRecentPhotos(limit: Int = 12) {
        updateMedia { it.copy(hasPhotoAccess = recentPhotosProvider.hasPhotoAccess()) }
        viewModelScope.launch {
            val result = runCatching { recentPhotosProvider.fetchRecent(limit) }
                .getOrElse { RecentPhotosResult.Granted(emptyList()) }
            updateMedia { it.copy(recentPhotos = result) }
        }
    }

    override fun onCleared() {
        stateUseCases.clearCurrentRoom(currentChatRoomId)
        super.onCleared()
    }

    private var collectJob: Job? = null

    private var roomJob: Job? = null

    private var usersFetchJob: Job? = null

    private var usersChangeJob: Job? = null

    private var currentChatRoomId: String = ""

    private var noMoreChats: Boolean = false

    private var fetchingMore: Boolean = false

    private var fetchingNewer: Boolean = false

    private val _firstUnreadChatId = MutableStateFlow<String?>(null)

    private var latestChats: List<Chat.Item> = emptyList()

    private fun updateSearch(transform: (ChatSearchState) -> ChatSearchState) {
        _uiState.update { it.copy(search = transform(it.search)) }
    }

    fun onAction(action: ChatRoomActions) {
        when (action) {
            is ChatRoomActions.Load -> load(action.chatRoomId)
            is ChatRoomActions.OnClose -> Unit
            is ChatRoomActions.OnReachEnd -> fetchMore()
            is ChatRoomActions.OnReachStart -> fetchNewer()
            is ChatRoomActions.OnEnterSearch -> {
                _uiState.update { it.copy(replyTarget = null, search = ChatSearchState(isActive = true)) }
            }
            is ChatRoomActions.OnExitSearch -> updateSearch { ChatSearchState() }
            is ChatRoomActions.OnSearchQueryChange -> updateSearchQuery(action.query)
            is ChatRoomActions.OnSearch -> {
                updateSearch { it.copy(searchedDate = null) }
                executeSearch()
            }
            is ChatRoomActions.OnSearchNext -> moveSearchIndex(+1)
            is ChatRoomActions.OnSearchPrev -> moveSearchIndex(-1)
            is ChatRoomActions.OnSearchUserSelect -> {
                updateSearch { it.copy(searchedUser = action.user, searchedDate = null) }
                executeSearch()
            }
            is ChatRoomActions.OnSelectSender -> {
                stateUseCases.selectSender(currentChatRoomId, action.user)
                _uiState.update { it.copy(sender = stateUseCases.getSender(currentChatRoomId)) }
            }
            is ChatRoomActions.OnSearchUserRemove -> {
                updateSearch { it.copy(searchedUser = null) }
                executeSearch()
            }
            is ChatRoomActions.OnSearchByDate -> searchByDate(action.datePrefix)
            is ChatRoomActions.OnSwipeReply -> {
                if (_uiState.value.search.isActive) {
                    updateSearch { ChatSearchState() }
                }
                viewModelScope.launch {
                    val chat = action.chat
                    val reply = chat.copy(chatContent = chatDisplayText(chat, forReply = true))
                    _uiState.update { it.copy(replyTarget = reply) }
                }
            }
            is ChatRoomActions.OnCancelReply -> _uiState.update { it.copy(replyTarget = null) }
            is ChatRoomActions.OnSendText -> sendText(action.text, action.emoticonId)
            is ChatRoomActions.OnResendFailedChat -> resendFailedChat(action.chatId)
            is ChatRoomActions.OnDeleteFailedChat -> deleteFailedChat(action.chatId)
            is ChatRoomActions.OnSendEmpathy -> sendEmpathy(action.targetChatId, action.empathyType)
            is ChatRoomActions.OnRecallChat -> recallChat(action.targetChatId)
            is ChatRoomActions.OnMoveToLatest -> moveToLatest()
            is ChatRoomActions.OnSendFiles -> sendFiles(action.paths)
            is ChatRoomActions.OnSubmitNotice -> submitNotice(action.content)
            is ChatRoomActions.OnDeleteNotice -> deleteNoticeAction()
            is ChatRoomActions.OnFocusChat -> focusOnChat(action.chatId)
            is ChatRoomActions.OnAddBookmark -> addBookmark(action.chat)
            is ChatRoomActions.OnDeleteBookmark -> deleteBookmark(action.chatId)
            is ChatRoomActions.OnTranslateChat -> translateChat(action.chatId, action.languageCode)
            is ChatRoomActions.OnHideTranslation ->
                _uiState.update { it.copy(translations = it.translations - action.chatId) }
            is ChatRoomActions.OnPolishText -> polishText(action.text, action.style)
            is ChatRoomActions.OnPolishHandled -> _uiState.update { it.copy(polish = PolishUiState.Idle) }
            is ChatRoomActions.OnEnterRoom ->
                runCatching { stateUseCases.openChatRoom(action.chatRoomId) }
            is ChatRoomActions.OnLeaveRoom ->
                runCatching { stateUseCases.closeChatRoom(action.chatRoomId) }
            is ChatRoomActions.OnResumeRoom ->
                runCatching { stateUseCases.cancelChatRoomNotification(action.chatRoomId) }
            is ChatRoomActions.OnLoadNoticeBar -> loadNoticeBarState(action.identityKey)
            is ChatRoomActions.OnNoticeExpandedChange -> {
                stateUseCases.setNoticeCollapsed(action.identityKey, !action.expanded)
                updateNoticeBar { it.copy(expanded = action.expanded) }
            }
            is ChatRoomActions.OnNoticeDetailsChange -> {
                stateUseCases.setNoticeDetailsShown(action.identityKey, action.shown)
                updateNoticeBar { it.copy(showDetails = action.shown) }
            }
            is ChatRoomActions.OnHideNotice -> {
                stateUseCases.hideNotice(action.identityKey)
                updateNoticeBar { it.copy(hidden = true) }
            }
            is ChatRoomActions.OnLoadRecentPhotos -> loadRecentPhotos()
            is ChatRoomActions.OnLoadGalleryPhotos -> loadGalleryPhotos()
            is ChatRoomActions.OnSelectGalleryAlbum -> selectGalleryAlbum(action.albumId)
            is ChatRoomActions.OnRequestPhotoAccess -> recentPhotosProvider.requestPhotoAccess()
            is ChatRoomActions.OnLaunchFilePicker -> launchFilePicker()
            is ChatRoomActions.OnEmoticonTabSelected -> stateUseCases.setLastEmoticonTab(action.index)
        }
    }

    private fun submitNotice(content: String) {
        val roomId = currentChatRoomId
        val trimmed = content.trim()
        if (roomId.isBlank() || trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching { chatRoomUseCases.addNotice(roomId, trimmed) }
                .onFailure { Log.message("[ChatRoomVM] addNotice failed: ${it.message}") }
        }
    }

    private fun deleteNoticeAction() {
        val roomId = currentChatRoomId
        if (roomId.isBlank()) return
        viewModelScope.launch {
            runCatching { chatRoomUseCases.deleteNotice(roomId) }
                .onFailure { Log.message("[ChatRoomVM] deleteNotice failed: ${it.message}") }
        }
    }

    private fun sendFiles(paths: List<String>) {
        if (paths.isEmpty() || currentChatRoomId.isBlank()) return
        val roomId = currentChatRoomId

        noMoreChats = false
        fetchingMore = false

        viewModelScope.launch {
            reinviteExitedPartnerIfNeeded()
            paths.forEach { path ->
                runCatching {
                    chatRoomUseCases.sendFile(roomId, path)
                }.onFailure { Log.message("[ChatRoomVM] sendFile failed ($path): ${it.message}") }
            }
        }
    }

    /**
     * 열려 있는 방에 대한 단발 동작 — 방 id 가 없으면 no-op, 실패는 로그만.
     * @param what 실패 로그에 남길 동작 이름.
     */
    private fun inRoom(what: String, block: suspend (roomId: String) -> Unit) {
        val roomId = currentChatRoomId
        if (roomId.isBlank()) return
        viewModelScope.launch {
            runCatching { block(roomId) }
                .onFailure { Log.message("[ChatRoomVM] $what failed: ${it.message}") }
        }
    }

    private fun moveToLatest() {
        // 최신으로 점프하면 과거 페이징 상태를 처음으로 되돌려야 한다.
        noMoreChats = false
        fetchingMore = false
        inRoom("moveToLatest") { chatRoomUseCases.loadLatestChats(it) }
    }

    private fun recallChat(targetChatId: String) {
        if (targetChatId.isBlank()) return
        inRoom("recallChat") { chatRoomUseCases.recallChat(it, targetChatId) }
    }

    private fun sendEmpathy(targetChatId: String, empathyType: String) {
        if (targetChatId.isBlank()) return
        inRoom("sendEmpathy") { chatRoomUseCases.sendEmpathy(it, targetChatId, empathyType) }
    }

    private fun resendFailedChat(chatId: String) {
        if (chatId.isBlank()) return
        inRoom("resendFailedChat") { chatRoomUseCases.resendFailedChat(it, chatId) }
    }

    private fun deleteFailedChat(chatId: String) {
        if (chatId.isBlank()) return
        inRoom("deleteFailedChat") { chatRoomUseCases.deleteFailedChat(it, chatId) }
    }

    private fun sendText(rawText: String, emoticonId: String? = null) {
        val text = rawText.trim()
        val hasEmoticon = !emoticonId.isNullOrBlank()
        if (text.isEmpty() && !hasEmoticon) return
        if (currentChatRoomId.isBlank()) return

        val reply = _uiState.value.replyTarget
        _uiState.update { it.copy(replyTarget = null) }

        noMoreChats = false
        fetchingMore = false

        viewModelScope.launch {
            reinviteExitedPartnerIfNeeded()
            runCatching {
                chatRoomUseCases.sendTextChat(
                    chatRoomId = currentChatRoomId,
                    text = text,
                    replyTarget = reply,
                    emoticonId = emoticonId
                )
            }.onFailure { e ->
                Log.message(
                    "[ChatRoomVM] sendText failed: ${e.message}"
                )
            }
        }
    }

    /** 1:1 방에서 퇴장한 상대에게 메시지를 보내면 전송 전에 자동 재초대. */
    private suspend fun reinviteExitedPartnerIfNeeded() {
        val room = chatRoomListUseCases.getChatRooms().first()
            .firstOrNull { it.id == currentChatRoomId } ?: return
        val partner = room.abandonedDirectPartner ?: return
        runCatching {
            inviteUseCases.inviteUsers(
                chatRoomId = currentChatRoomId,
                invitedUsers = listOf(partner),
                existingUserCount = room.displayUserCount,
            )
            Log.message("[ChatRoomVM] auto re-invited exited partner ${partner.first} to $currentChatRoomId")
        }.onFailure { Log.message("[ChatRoomVM] auto re-invite failed: ${it.message}") }
    }

    private fun load(chatRoomId: String) {
        if (chatRoomId.isBlank()) {
            _uiState.update { it.copy(messages = ChatMessagesUiState.Error("Invalid room id")) }
            return
        }
        currentChatRoomId = chatRoomId
        noMoreChats = false
        fetchingMore = false
        fetchingNewer = false
        _firstUnreadChatId.value = null
        _uiState.update {
            it.copy(
                chatRoom = null,
                users = emptyList(),
                sender = stateUseCases.getSender(chatRoomId),
                search = ChatSearchState(),
                replyTarget = null,
                translations = emptyMap(),
                polish = PolishUiState.Idle,
                initialEmoticonTab = stateUseCases.getLastEmoticonTab(),
            )
        }
        latestChats = emptyList()

        viewModelScope.launch {
            if (currentChatRoomId != chatRoomId) return@launch
            chatRoomUseCases.fetchBookmarks(chatRoomId)
        }

        viewModelScope.launch {
            if (currentChatRoomId != chatRoomId) return@launch
            chatRoomUseCases.requestNotice(chatRoomId)
        }

        val roomFlow = chatRoomListUseCases.getChatRooms()
            .map { rooms -> rooms.firstOrNull { it.id == chatRoomId } }
            .distinctUntilChanged()

        roomJob?.cancel()
        roomJob = viewModelScope.launch {
            roomFlow.collect { room -> _uiState.update { it.copy(chatRoom = room) } }
        }

        val isDirectRoomFlow = roomFlow
            .map { it?.isDirectRoom ?: false }
            .distinctUntilChanged()

        val searchFlow = _uiState
            .map { it.search }
            .distinctUntilChanged()

        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            combine(
                chatRoomUseCases.getChats(chatRoomId),
                _firstUnreadChatId,
                searchFlow,
                isDirectRoomFlow,
            ) { chats, marker, search, isDirect ->
                latestChats = chats
                val visible = if (isDirect) {
                    chats.filterNot { it.chatType == Chat.Type.INVITE || it.chatType == Chat.Type.EXIT }
                } else chats
                val grouped = groupChatsForUi(
                    chats = visible.asReversed(),
                    firstUnreadChatId = marker,
                    matchedChatIds = search.matchedChatIds.toSet(),
                    currentMatchChatId = search.currentMatchChatId,
                    focusChatId = search.focusChatId
                )
                ChatMessagesUiState.Success(grouped)
            }.collect { messages -> _uiState.update { it.copy(messages = messages) } }
        }

        usersFetchJob?.cancel()
        usersFetchJob = viewModelScope.launch {
            val result = runCatching { chatRoomUseCases.fetchChatRoomUsers(chatRoomId) }
                .getOrNull().orEmpty()
            if (currentChatRoomId == chatRoomId) {
                _uiState.update { it.copy(users = result) }
            }
        }

        usersChangeJob?.cancel()
        usersChangeJob = viewModelScope.launch {
            stateUseCases.observeRoomUsersChanged()
                .filter { it == chatRoomId }
                .collect {
                    val result = runCatching { chatRoomUseCases.fetchChatRoomUsers(chatRoomId) }
                        .getOrNull().orEmpty()
                    if (currentChatRoomId == chatRoomId) {
                        _uiState.update { it.copy(users = result) }
                    }
                }
        }

        viewModelScope.launch {
            runCatching {
                var entryEmitted = false

                if (_firstUnreadChatId.value == null && currentChatRoomId == chatRoomId) {
                    val room = chatRoomListUseCases.getChatRooms()
                        .first()
                        .firstOrNull { it.id == chatRoomId }
                    val unread = room?.unReadCount?.toIntOrNull() ?: 0
                    val serverLastChatId = room?.lastChatID.orEmpty()
                    if (unread > 0 && serverLastChatId.isNotBlank()) {
                        val cached = chatRoomUseCases.getChats(chatRoomId).first()
                        if (cached.lastOrNull()?.chatID == serverLastChatId && cached.size >= unread) {
                            _firstUnreadChatId.value = cached[cached.size - unread].chatID
                            _entryScroll.tryEmit(_firstUnreadChatId.value)
                            entryEmitted = true
                        }
                    }
                }

                chatRoomUseCases.fetchChats(chatRoomId)
                chatRoomUseCases.fetchNewerChats(chatRoomId)

                if (_firstUnreadChatId.value == null && currentChatRoomId == chatRoomId) {
                    val room = chatRoomListUseCases.getChatRooms()
                        .first()
                        .firstOrNull { it.id == chatRoomId }
                    val unread = room?.unReadCount?.toIntOrNull() ?: 0
                    val serverLastChatId = room?.lastChatID.orEmpty()
                    if (unread > 0) {
                        var allChats = chatRoomUseCases.getChats(chatRoomId).first()

                        if (serverLastChatId.isNotBlank() &&
                            allChats.lastOrNull()?.chatID != serverLastChatId
                        ) {
                            runCatching { chatRoomUseCases.fetchChats(chatRoomId) }
                            runCatching { chatRoomUseCases.fetchNewerChats(chatRoomId) }
                            allChats = chatRoomUseCases.getChats(chatRoomId).first()
                        }

                        val synced = serverLastChatId.isBlank() ||
                            allChats.lastOrNull()?.chatID == serverLastChatId
                        if (synced && allChats.size >= unread) {
                            _firstUnreadChatId.value = allChats[allChats.size - unread].chatID
                        } else {
                            Log.message(
                                "[UnreadMarker] skip — not synced(synced=$synced " +
                                    "localLast=${allChats.lastOrNull()?.chatID?.takeLast(8)} " +
                                    "serverLast=${serverLastChatId.takeLast(8)}) size=${allChats.size} unread=$unread"
                            )
                        }
                    }
                }

                if (!entryEmitted && currentChatRoomId == chatRoomId) {
                    _entryScroll.tryEmit(_firstUnreadChatId.value)
                }

                if (currentChatRoomId == chatRoomId &&
                    stateUseCases.isCurrentChatRoom(chatRoomId)
                ) {
                    val lastChatId = chatRoomUseCases.getChats(chatRoomId)
                        .first()
                        .lastOrNull { it.chatStatus != Chat.Status.SENDING && it.chatStatus != Chat.Status.FAIL }
                        ?.chatID
                        .orEmpty()

                    if (lastChatId.isNotBlank()) {
                        chatRoomUseCases.markChatAsRead(chatRoomId, lastChatId)
                    }
                }
            }.onFailure { e ->
                if (_uiState.value.messages is ChatMessagesUiState.Loading) {
                    _uiState.update {
                        it.copy(messages = ChatMessagesUiState.Error(e.message ?: "Failed to load chats"))
                    }
                }
            }
        }

        viewModelScope.launch {
            stateUseCases.observeNewChat()
                .filter { it == chatRoomId && currentChatRoomId == chatRoomId }
                .collect {
                    if (!stateUseCases.isCurrentChatRoom(chatRoomId)) return@collect

                    val lastChatId = chatRoomUseCases.getChats(chatRoomId)
                        .first()
                        .lastOrNull { it.chatStatus != Chat.Status.SENDING && it.chatStatus != Chat.Status.FAIL }
                        ?.chatID
                        .orEmpty()
                    if (lastChatId.isNotBlank()) {
                        runCatching { chatRoomUseCases.markChatAsRead(chatRoomId, lastChatId) }
                    }
                }
        }
    }

    private fun fetchMore() {
        val id = currentChatRoomId
        if (id.isBlank() || noMoreChats || fetchingMore) return
        fetchingMore = true
        viewModelScope.launch {
            runCatching { chatRoomUseCases.fetchMoreChats(id) }
                .onSuccess { hasMore -> if (!hasMore) noMoreChats = true }
                .also { fetchingMore = false }
        }
    }

    private fun fetchNewer() {
        val id = currentChatRoomId
        if (id.isBlank() || fetchingNewer) return
        fetchingNewer = true
        viewModelScope.launch {
            runCatching { chatRoomUseCases.fetchNewerChats(id) }
                .also { fetchingNewer = false }
        }
    }


    private fun updateSearchQuery(query: String) {
        updateSearch { it.copy(query = query) }
        if (query.isEmpty()) {
            executeSearch()
        }
    }

    private fun executeSearch() {
        val roomId = currentChatRoomId
        if (roomId.isBlank()) return
        val state = _uiState.value.search
        val q = state.query
        val u = state.searchedUser
        val d = state.searchedDate

        if (q.isEmpty() && u == null && d.isNullOrBlank()) {
            updateSearch {
                it.copy(
                    committedQuery = "",
                    matchedChatIds = emptyList(),
                    currentIndex = -1,
                    focusChatId = null
                )
            }
            return
        }

        updateSearch { it.copy(committedQuery = q) }

        viewModelScope.launch {
            val dateFrom = if (d.isNullOrBlank()) "" else "$d 00:00:00:000"
            val dateTo   = if (d.isNullOrBlank()) "" else "$d 23:59:59:999"

            val rawDesc = runCatching {
                chatRoomUseCases.searchChats(
                    chatRoomId = roomId,
                    query = q,
                    userId = u?.id.orEmpty(),
                    dateFrom = dateFrom,
                    dateTo = dateTo
                )
            }.getOrElse { emptyList() }

            val idsAsc = rawDesc.sortedBy { it.second }.map { it.first }

            if (idsAsc.isEmpty()) {
                updateSearch {
                    it.copy(
                        matchedChatIds = emptyList(),
                        currentIndex = -1,
                        focusChatId = null
                    )
                }
                return@launch
            }

            val focusId = idsAsc.last()
            updateSearch {
                it.copy(
                    matchedChatIds = idsAsc,
                    currentIndex = idsAsc.lastIndex,
                )
            }
            focusOnChat(focusId)
        }
    }

    private fun searchByDate(datePrefix: String) {
        if (datePrefix.isBlank()) return
        updateSearch {
            it.copy(
                query = "",
                searchedUser = null,
                searchedDate = datePrefix,
                matchedChatIds = emptyList(),
                currentIndex = -1,
                focusChatId = null
            )
        }
        executeSearch()
    }

    private fun moveSearchIndex(delta: Int) {
        val state = _uiState.value.search
        if (state.matchedChatIds.isEmpty()) return
        val newIndex = (state.currentIndex + delta)
            .coerceIn(0, state.matchedChatIds.lastIndex)
        if (newIndex == state.currentIndex) return
        val targetId = state.matchedChatIds[newIndex]

        updateSearch { it.copy(currentIndex = newIndex) }
        focusOnChat(targetId)
    }

    private fun addBookmark(chat: Chat.Item) {
        val roomId = currentChatRoomId
        if (roomId.isBlank() || chat.chatID.isBlank()) return
        viewModelScope.launch {
            runCatching { chatRoomUseCases.addBookmark(roomId, chat) }
                .onFailure { Log.message("[ChatRoomVM] addBookmark failed: ${it.message}") }
        }
    }

    private fun deleteBookmark(chatId: String) {
        val roomId = currentChatRoomId
        if (roomId.isBlank() || chatId.isBlank()) return
        viewModelScope.launch {
            runCatching { chatRoomUseCases.deleteBookmark(roomId, chatId) }
                .onFailure { Log.message("[ChatRoomVM] deleteBookmark failed: ${it.message}") }
        }
    }

    /** 번역을 받는 동안에는 말풍선 아래에 진행 표시를 두고, 받지 못하면 걷어 낸 뒤 안내한다. */
    private fun translateChat(chatId: String, languageCode: String) {
        val roomId = currentChatRoomId
        if (roomId.isBlank() || chatId.isBlank() || chatId in _uiState.value.translations) return
        _uiState.update { it.copy(translations = it.translations + (chatId to ChatTranslationUiState.Loading)) }
        viewModelScope.launch {
            val translation = chatRoomUseCases.translateChat(roomId, chatId, languageCode)
            if (currentChatRoomId != roomId) return@launch
            if (translation == null) {
                _uiState.update { it.copy(translations = it.translations - chatId) }
                _events.trySend(ChatRoomEvent.TranslationFailed)
            } else {
                _uiState.update {
                    it.copy(translations = it.translations + (chatId to ChatTranslationUiState.Ready(translation)))
                }
            }
        }
    }

    private fun polishText(text: String, style: PolishStyle) {
        val roomId = currentChatRoomId
        if (text.isBlank() || _uiState.value.polish == PolishUiState.Loading) return
        _uiState.update { it.copy(polish = PolishUiState.Loading) }
        viewModelScope.launch {
            val polished = chatRoomUseCases.polishText(text, style)
            if (currentChatRoomId != roomId) return@launch
            val result = polished?.let { PolishUiState.Ready(text, it) } ?: PolishUiState.Failed
            _uiState.update { it.copy(polish = result) }
        }
    }

    private fun focusOnChat(chatId: String) {
        val roomId = currentChatRoomId
        if (roomId.isBlank() || chatId.isBlank()) return
        viewModelScope.launch {
            if (latestChats.none { it.chatID == chatId }) {
                runCatching { chatRoomUseCases.loadChatWithContext(roomId, chatId) }
                    .onFailure { Log.message("[Focus] loadChatWithContext failed: ${it.message}") }
                if (currentChatRoomId != roomId) return@launch
            }
            updateSearch {
                it.copy(
                    focusChatId = chatId,
                    focusTriggerKey = it.focusTriggerKey + 1,
                )
            }
        }
    }
}


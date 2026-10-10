package com.eunilsung.talk.ui.chatroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.repository.SenderOverrideRepository
import com.eunilsung.talk.data.local.FilePickerProvider
import com.eunilsung.talk.data.local.NoticeUiStateStore
import com.eunilsung.talk.data.local.PhotoAlbum
import com.eunilsung.talk.data.local.RecentPhoto
import com.eunilsung.talk.data.local.RecentPhotosProvider
import com.eunilsung.talk.data.local.RecentPhotosResult
import com.eunilsung.talk.data.remote.push.CurrentChatRoomTracker
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.PolishStyle
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.repository.ChatRoomRepository
import com.eunilsung.talk.domain.repository.ChatSettingsRepository
import com.eunilsung.talk.domain.repository.ChatSettingsState
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases
import com.eunilsung.talk.domain.usecase.ChatRoomUseCases
import com.eunilsung.talk.util.Log
import com.eunilsung.talk.util.chatDisplayText

class ChatRoomViewModel(
    private val chatRoomUseCases: ChatRoomUseCases,
    private val chatRoomRepository: ChatRoomRepository,
    private val recentPhotosProvider: RecentPhotosProvider,
    private val filePickerProvider: FilePickerProvider,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val chatRoomListUseCases: ChatRoomListUseCases,
    private val inviteUseCases: com.eunilsung.talk.domain.usecase.InviteUseCases,
    private val senderOverride: SenderOverrideRepository,
    private val noticeUiStateStore: NoticeUiStateStore,
) : ViewModel() {

    fun lastEmoticonTab(): Int = chatSettingsRepository.getLastEmoticonTab()
    fun saveEmoticonTab(index: Int) = chatSettingsRepository.setLastEmoticonTab(index)

    val chatSettings: StateFlow<ChatSettingsState> = chatSettingsRepository.state

    /** picker 결과가 싱글톤 provider 로 broadcast 되므로, 이 VM 이 띄운 요청만 소비하기 위한 태그. */
    private val filePickerRequestId =
        com.eunilsung.talk.data.local.FilePickerRequestId.next("chatroom")

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
    }

    fun launchFilePicker(allowMultiple: Boolean = true) {
        filePickerProvider.launchFilePicker(
            requestId = filePickerRequestId,
            allowMultiple = allowMultiple,
            mimeTypes = listOf("*/*"),
        )
    }

    private val _galleryPhotos = MutableStateFlow<List<RecentPhoto>>(emptyList())
    val galleryPhotos: StateFlow<List<RecentPhoto>> = _galleryPhotos.asStateFlow()

    private val _galleryAlbums = MutableStateFlow<List<PhotoAlbum>>(emptyList())

    /** 갤러리 시트 상단 필터에 뿌릴 앨범 목록. */
    val galleryAlbums: StateFlow<List<PhotoAlbum>> = _galleryAlbums.asStateFlow()

    private val _selectedAlbumId = MutableStateFlow<String?>(null)

    /** 선택된 앨범. null = 전체. */
    val selectedAlbumId: StateFlow<String?> = _selectedAlbumId.asStateFlow()

    private var galleryJob: Job? = null

    /** 앨범 선택 — 해당 폴더의 사진만 다시 읽는다. */
    fun selectGalleryAlbum(albumId: String?, limit: Int = 500) {
        _selectedAlbumId.value = albumId
        _galleryPhotos.value = emptyList()
        loadGalleryPhotos(limit)
    }

    /**
     * 갤러리 사진 로딩.
     *
     * 이전 로딩은 반드시 취소하고, 결과를 쓰기 전에 요청 당시 앨범이 아직 선택 상태인지 확인한다.
     * iOS 는 썸네일을 디코드하며 onPartial 로 중간 결과를 계속 흘리므로, 앨범을 빠르게 오가면
     * 이전 앨범의 늦은 콜백이 새 목록을 덮어써 엉뚱한 사진이 보인다.
     * (취소는 즉시 전파되지 않아 이미 출발한 콜백이 남을 수 있어 확인이 따로 필요하다.)
     */
    fun loadGalleryPhotos(limit: Int = 500) {
        val albumId = _selectedAlbumId.value
        galleryJob?.cancel()
        galleryJob = viewModelScope.launch {
            fun apply(r: RecentPhotosResult) {
                if (_selectedAlbumId.value != albumId) return
                _galleryPhotos.value = when (r) {
                    is RecentPhotosResult.Granted -> r.photos
                    is RecentPhotosResult.Limited -> r.photos
                    is RecentPhotosResult.Denied -> emptyList()
                }
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
        if (_galleryAlbums.value.isNotEmpty()) return
        viewModelScope.launch {
            _galleryAlbums.value = runCatching { recentPhotosProvider.fetchAlbums() }
                .getOrElse { emptyList() }
        }
    }

    val newChatPush: SharedFlow<String> = chatRoomRepository.newChatPush

    val mySendPush: SharedFlow<String> = chatRoomRepository.mySendPush

    val latestLoadedPush: SharedFlow<String> = chatRoomRepository.latestLoadedPush

    val selfLeftPush: SharedFlow<String> = chatRoomRepository.selfLeftPush

    private val _entryScroll = MutableSharedFlow<String?>(replay = 1, extraBufferCapacity = 1)
    val entryScroll: SharedFlow<String?> = _entryScroll

    val currentNotice: StateFlow<com.eunilsung.talk.domain.model.Notice?> =
        chatRoomRepository.currentNotice

    private val _noticeBar = MutableStateFlow(NoticeBarUiState())

    /** 공지바 표시 상태 — Settings 에 영속되며 화면은 이 값만 읽는다. */
    val noticeBar: StateFlow<NoticeBarUiState> = _noticeBar.asStateFlow()

    /** 공지가 바뀔 때 저장된 표시 상태를 읽어 온다. */
    fun loadNoticeBarState(identityKey: String) {
        _noticeBar.value = NoticeBarUiState(
            hidden = noticeUiStateStore.isHidden(identityKey),
            expanded = !noticeUiStateStore.isCollapsed(identityKey),
            showDetails = noticeUiStateStore.isDetailsShown(identityKey),
        )
    }

    fun setNoticeExpanded(identityKey: String, expanded: Boolean) {
        noticeUiStateStore.setCollapsed(identityKey, !expanded)
        _noticeBar.update { it.copy(expanded = expanded) }
    }

    fun setNoticeDetailsShown(identityKey: String, shown: Boolean) {
        noticeUiStateStore.setDetailsShown(identityKey, shown)
        _noticeBar.update { it.copy(showDetails = shown) }
    }

    fun hideNoticePermanently(identityKey: String) {
        noticeUiStateStore.setHidden(identityKey, true)
        _noticeBar.update { it.copy(hidden = true) }
    }

    val bookmarks: StateFlow<List<com.eunilsung.talk.domain.model.Bookmark>> =
        chatRoomRepository.bookmarks

    private val _recentPhotos = MutableStateFlow<RecentPhotosResult>(
        RecentPhotosResult.Granted(emptyList())
    )
    val recentPhotos: StateFlow<RecentPhotosResult> = _recentPhotos.asStateFlow()

    fun loadRecentPhotos(limit: Int = 12) {
        viewModelScope.launch {
            _recentPhotos.value = runCatching { recentPhotosProvider.fetchRecent(limit) }
                .getOrElse { RecentPhotosResult.Granted(emptyList()) }
        }
    }

    fun requestPhotoAccess() {
        recentPhotosProvider.requestPhotoAccess()
    }

    fun hasPhotoAccess(): Boolean = recentPhotosProvider.hasPhotoAccess()

    override fun onCleared() {
        chatRoomRepository.clearCurrentRoom(currentChatRoomId)
        super.onCleared()
    }

    private val _uiState = MutableStateFlow<ChatRoomUiState>(ChatRoomUiState.Loading)
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    private var collectJob: Job? = null

    private var usersFetchJob: Job? = null

    private var usersChangeJob: Job? = null

    private var currentChatRoomId: String = ""

    private var noMoreChats: Boolean = false

    private var fetchingMore: Boolean = false

    private var fetchingNewer: Boolean = false

    private val _firstUnreadChatId = MutableStateFlow<String?>(null)

    private val _translations = MutableStateFlow<Map<String, ChatTranslationUiState>>(emptyMap())

    /** 대화 id 별 번역. 이 화면에서만 들고 있는다. */
    val translations: StateFlow<Map<String, ChatTranslationUiState>> = _translations.asStateFlow()

    private val _polish = MutableStateFlow<PolishUiState>(PolishUiState.Idle)

    /** 보내기 전의 글 다듬기 상태. */
    val polish: StateFlow<PolishUiState> = _polish.asStateFlow()

    private val _translationFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 번역을 받지 못했다 — 화면이 안내를 한 번 띄운다. */
    val translationFailed: SharedFlow<Unit> = _translationFailed.asSharedFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    /** 이 방의 전송 주체 — `null` 이면 나. 상단바 아이콘 강조와 선택 목록 표시에 쓴다. */
    private val _sender = MutableStateFlow<User?>(null)
    val sender: StateFlow<User?> = _sender.asStateFlow()

    private val _searchState = MutableStateFlow(ChatSearchState())
    val searchState: StateFlow<ChatSearchState> = _searchState.asStateFlow()

    private val _replyTarget = MutableStateFlow<Chat.Item?>(null)
    val replyTarget: StateFlow<Chat.Item?> = _replyTarget.asStateFlow()

    private var latestChats: List<Chat.Item> = emptyList()

    fun onAction(action: ChatRoomActions) {
        when (action) {
            is ChatRoomActions.Load -> load(action.chatRoomId)
            is ChatRoomActions.OnClose -> Unit
            is ChatRoomActions.OnReachEnd -> fetchMore()
            is ChatRoomActions.OnReachStart -> fetchNewer()
            is ChatRoomActions.OnEnterSearch -> {
                _replyTarget.value = null
                _searchState.update { ChatSearchState(isActive = true) }
            }
            is ChatRoomActions.OnExitSearch -> {
                _searchState.update { ChatSearchState() }
            }
            is ChatRoomActions.OnSearchQueryChange -> updateSearchQuery(action.query)
            is ChatRoomActions.OnSearch -> {
                _searchState.update { it.copy(searchedDate = null) }
                executeSearch()
            }
            is ChatRoomActions.OnSearchNext -> moveSearchIndex(+1)
            is ChatRoomActions.OnSearchPrev -> moveSearchIndex(-1)
            is ChatRoomActions.OnSearchUserSelect -> {
                _searchState.update { it.copy(searchedUser = action.user, searchedDate = null) }
                executeSearch()
            }
            is ChatRoomActions.OnSelectSender -> {
                senderOverride.select(currentChatRoomId, action.user)
                _sender.value = senderOverride.senderFor(currentChatRoomId)
            }
            is ChatRoomActions.OnSearchUserRemove -> {
                _searchState.update { it.copy(searchedUser = null) }
                executeSearch()
            }
            is ChatRoomActions.OnSearchByDate -> searchByDate(action.datePrefix)
            is ChatRoomActions.OnSwipeReply -> {
                if (_searchState.value.isActive) {
                    _searchState.update { ChatSearchState() }
                }
                viewModelScope.launch {
                    val chat = action.chat
                    _replyTarget.value = chat.copy(chatContent = chatDisplayText(chat, forReply = true))
                }
            }
            is ChatRoomActions.OnCancelReply -> _replyTarget.value = null
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
            is ChatRoomActions.OnHideTranslation -> _translations.update { it - action.chatId }
            is ChatRoomActions.OnPolishText -> polishText(action.text, action.style)
            is ChatRoomActions.OnPolishHandled -> _polish.value = PolishUiState.Idle
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

        val reply = _replyTarget.value
        _replyTarget.value = null

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
            _uiState.value = ChatRoomUiState.Error("Invalid room id")
            return
        }
        currentChatRoomId = chatRoomId
        noMoreChats = false
        fetchingMore = false
        fetchingNewer = false
        _firstUnreadChatId.value = null
        _translations.value = emptyMap()
        _polish.value = PolishUiState.Idle
        _users.value = emptyList()
        _sender.value = senderOverride.senderFor(chatRoomId)
        _searchState.value = ChatSearchState()
        _replyTarget.value = null
        latestChats = emptyList()

        viewModelScope.launch {
            if (currentChatRoomId != chatRoomId) return@launch
            chatRoomUseCases.fetchBookmarks(chatRoomId)
        }

        viewModelScope.launch {
            if (currentChatRoomId != chatRoomId) return@launch
            chatRoomUseCases.requestNotice(chatRoomId)
        }

        val isDirectRoomFlow = chatRoomListUseCases.getChatRooms()
            .map { rooms -> rooms.firstOrNull { it.id == chatRoomId }?.isDirectRoom ?: false }
            .distinctUntilChanged()

        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            combine(
                chatRoomUseCases.getChats(chatRoomId),
                _firstUnreadChatId,
                _searchState,
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
                ChatRoomUiState.Success(grouped) as ChatRoomUiState
            }.collect { _uiState.value = it }
        }

        usersFetchJob?.cancel()
        usersFetchJob = viewModelScope.launch {
            val result = runCatching { chatRoomUseCases.fetchChatRoomUsers(chatRoomId) }
                .getOrNull().orEmpty()
            if (currentChatRoomId == chatRoomId) {
                _users.value = result
            }
        }

        usersChangeJob?.cancel()
        usersChangeJob = viewModelScope.launch {
            chatRoomRepository.roomUsersChanged
                .filter { it == chatRoomId }
                .collect {
                    val result = runCatching { chatRoomUseCases.fetchChatRoomUsers(chatRoomId) }
                        .getOrNull().orEmpty()
                    if (currentChatRoomId == chatRoomId) {
                        _users.value = result
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
                    CurrentChatRoomTracker.currentChatRoomId == chatRoomId
                ) {
                    val lastChatId = chatRoomUseCases.getChats(chatRoomId)
                        .first()
                        .lastOrNull { it.chatStatue != Chat.Statue.SENDING && it.chatStatue != Chat.Statue.FAIL }
                        ?.chatID
                        .orEmpty()

                    if (lastChatId.isNotBlank()) {
                        chatRoomUseCases.markChatAsRead(chatRoomId, lastChatId)
                    }
                }
            }.onFailure { e ->
                if (_uiState.value is ChatRoomUiState.Loading) {
                    _uiState.value = ChatRoomUiState.Error(e.message ?: "Failed to load chats")
                }
            }
        }

        viewModelScope.launch {
            chatRoomRepository.newChatPush
                .filter { it == chatRoomId && currentChatRoomId == chatRoomId }
                .collect {
                    if (CurrentChatRoomTracker.currentChatRoomId != chatRoomId) return@collect

                    val lastChatId = chatRoomUseCases.getChats(chatRoomId)
                        .first()
                        .lastOrNull { it.chatStatue != Chat.Statue.SENDING && it.chatStatue != Chat.Statue.FAIL }
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
        _searchState.update { it.copy(query = query) }
        if (query.isEmpty()) {
            executeSearch()
        }
    }

    private fun executeSearch() {
        val roomId = currentChatRoomId
        if (roomId.isBlank()) return
        val state = _searchState.value
        val q = state.query
        val u = state.searchedUser
        val d = state.searchedDate

        if (q.isEmpty() && u == null && d.isNullOrBlank()) {
            _searchState.update {
                it.copy(
                    committedQuery = "",
                    matchedChatIds = emptyList(),
                    currentIndex = -1,
                    focusChatId = null
                )
            }
            return
        }

        _searchState.update { it.copy(committedQuery = q) }

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
                _searchState.update {
                    it.copy(
                        matchedChatIds = emptyList(),
                        currentIndex = -1,
                        focusChatId = null
                    )
                }
                return@launch
            }

            val focusId = idsAsc.last()
            _searchState.update {
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
        _searchState.update {
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
        val state = _searchState.value
        if (state.matchedChatIds.isEmpty()) return
        val newIndex = (state.currentIndex + delta)
            .coerceIn(0, state.matchedChatIds.lastIndex)
        if (newIndex == state.currentIndex) return
        val targetId = state.matchedChatIds[newIndex]

        _searchState.update { it.copy(currentIndex = newIndex) }
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
        if (roomId.isBlank() || chatId.isBlank() || chatId in _translations.value) return
        _translations.update { it + (chatId to ChatTranslationUiState.Loading) }
        viewModelScope.launch {
            val translation = chatRoomUseCases.translateChat(roomId, chatId, languageCode)
            if (currentChatRoomId != roomId) return@launch
            if (translation == null) {
                _translations.update { it - chatId }
                _translationFailed.tryEmit(Unit)
            } else {
                _translations.update { it + (chatId to ChatTranslationUiState.Ready(translation)) }
            }
        }
    }

    private fun polishText(text: String, style: PolishStyle) {
        val roomId = currentChatRoomId
        if (text.isBlank() || _polish.value == PolishUiState.Loading) return
        _polish.value = PolishUiState.Loading
        viewModelScope.launch {
            val polished = chatRoomUseCases.polishText(text, style)
            if (currentChatRoomId != roomId) return@launch
            _polish.value = polished?.let { PolishUiState.Ready(text, it) } ?: PolishUiState.Failed
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
            _searchState.update {
                it.copy(
                    focusChatId = chatId,
                    focusTriggerKey = it.focusTriggerKey + 1,
                )
            }
        }
    }
}


package com.eunilsung.talk.ui.chatroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.PolishStyle
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases
import com.eunilsung.talk.domain.usecase.ChatRoomUseCases
import com.eunilsung.talk.util.Log
import com.eunilsung.talk.util.chatDisplayText
import com.eunilsung.talk.domain.usecase.ChatRoomStateUseCases

class ChatRoomViewModel(
    private val chatRoomUseCases: ChatRoomUseCases,
    private val stateUseCases: ChatRoomStateUseCases,
    recentPhotosProvider: RecentPhotosProvider,
    filePickerProvider: FilePickerProvider,
    private val chatRoomListUseCases: ChatRoomListUseCases,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatRoomUiState(
            chatSettings = stateUseCases.observeChatSettings().value,
            initialEmoticonTab = stateUseCases.getLastEmoticonTab(),
            media = ChatMediaUiState(hasPhotoAccess = recentPhotosProvider.hasPhotoAccess()),
        )
    )
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    private var currentChatRoomId: String = ""

    private var latestChats: List<Chat.Item> = emptyList()

    private val media = ChatMediaHandler(viewModelScope, _uiState, recentPhotosProvider, filePickerProvider)

    private val search = ChatSearchHandler(
        scope = viewModelScope,
        uiState = _uiState,
        chatRoomUseCases = chatRoomUseCases,
        currentRoomId = { currentChatRoomId },
        loadedChats = { latestChats },
    )

    private val read = ChatReadHandler(
        scope = viewModelScope,
        chatRoomUseCases = chatRoomUseCases,
        chatRoomListUseCases = chatRoomListUseCases,
        stateUseCases = stateUseCases,
        currentRoomId = { currentChatRoomId },
    )

    /** 저장소가 밀어 주는 일회성 신호 — 대화 목록의 스크롤과 새 대화 배너가 쓴다. */
    val signals: ChatRoomSignals = ChatRoomSignals(
        newChat = stateUseCases.observeNewChat(),
        mySend = stateUseCases.observeMySend(),
        latestLoaded = stateUseCases.observeLatestLoaded(),
        entryScroll = read.entryScroll,
    )

    private val _events = Channel<ChatRoomEvent>(Channel.BUFFERED)
    val events: Flow<ChatRoomEvent> = _events.receiveAsFlow()

    init {
        media.collectPickedFiles { paths -> onAction(ChatRoomActions.OnSendFiles(paths)) }
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

    override fun onCleared() {
        stateUseCases.clearCurrentRoom(currentChatRoomId)
        super.onCleared()
    }

    private var collectJob: Job? = null

    private var roomJob: Job? = null

    private var usersFetchJob: Job? = null

    private var usersChangeJob: Job? = null

    private var noMoreChats: Boolean = false

    private var fetchingMore: Boolean = false

    private var fetchingNewer: Boolean = false

    fun onAction(action: ChatRoomActions) {
        when (action) {
            is ChatRoomActions.Load -> load(action.chatRoomId)
            is ChatRoomActions.OnClose -> Unit
            is ChatRoomActions.OnReachEnd -> fetchMore()
            is ChatRoomActions.OnReachStart -> fetchNewer()
            is ChatRoomActions.OnEnterSearch -> {
                _uiState.update { it.copy(replyTarget = null, search = ChatSearchUiState(isActive = true)) }
            }
            is ChatRoomActions.OnExitSearch -> search.update { ChatSearchUiState() }
            is ChatRoomActions.OnSearchQueryChange -> search.updateQuery(action.query)
            is ChatRoomActions.OnSearch -> search.searchByQuery()
            is ChatRoomActions.OnSearchNext -> search.moveIndex(+1)
            is ChatRoomActions.OnSearchPrev -> search.moveIndex(-1)
            is ChatRoomActions.OnSearchUserSelect -> search.selectUser(action.user)
            is ChatRoomActions.OnSelectSender -> {
                stateUseCases.selectSender(currentChatRoomId, action.user)
                _uiState.update { it.copy(sender = stateUseCases.getSender(currentChatRoomId)) }
            }
            is ChatRoomActions.OnSearchUserRemove -> search.removeUser()
            is ChatRoomActions.OnSearchByDate -> search.searchByDate(action.datePrefix)
            is ChatRoomActions.OnSwipeReply -> {
                if (_uiState.value.search.isActive) {
                    search.update { ChatSearchUiState() }
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
            is ChatRoomActions.OnFocusChat -> search.focusOnChat(action.chatId)
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
            is ChatRoomActions.OnLoadRecentPhotos -> media.loadRecentPhotos()
            is ChatRoomActions.OnLoadGalleryPhotos -> media.loadGalleryPhotos()
            is ChatRoomActions.OnSelectGalleryAlbum -> media.selectGalleryAlbum(action.albumId)
            is ChatRoomActions.OnRequestPhotoAccess -> media.requestPhotoAccess()
            is ChatRoomActions.OnLaunchFilePicker -> media.launchFilePicker()
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
        runCatching { chatRoomUseCases.reinviteDirectPartner(currentChatRoomId) }
            .onFailure { Log.message("[ChatRoomVM] auto re-invite failed: ${it.message}") }
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
        read.reset()
        _uiState.update {
            it.copy(
                chatRoom = null,
                users = emptyList(),
                sender = stateUseCases.getSender(chatRoomId),
                myId = stateUseCases.getMyUser().id,
                search = ChatSearchUiState(),
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
                read.firstUnreadChatId,
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

        read.syncOnEnter(chatRoomId) { e ->
            if (_uiState.value.messages is ChatMessagesUiState.Loading) {
                _uiState.update {
                    it.copy(messages = ChatMessagesUiState.Error(e.message ?: "Failed to load chats"))
                }
            }
        }

        read.markReadOnNewChat(chatRoomId)
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
}

package com.eunilsung.talk.ui.chatroomlist

import com.eunilsung.talk.domain.util.validateChatRoomName
import com.eunilsung.talk.domain.util.ChatRoomNameValidation
import com.eunilsung.talk.domain.model.ALARM_OFF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.ui.uikit.search.Search
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases

class ChatRoomListViewModel(
    private val chatRoomListUseCases: ChatRoomListUseCases
) : ViewModel() {

    private val _chatRooms = MutableStateFlow<List<ChatRoom.Item>>(emptyList())

    private val _uiState = MutableStateFlow(
        ChatRoomListUiState(
            unreadTotal = chatRoomListUseCases.observeChatRoomUnreadTotal().value,
            isRefreshing = chatRoomListUseCases.observeIsFetching().value,
        )
    )
    val uiState: StateFlow<ChatRoomListUiState> = _uiState.asStateFlow()

    private val _events = Channel<ChatRoomListEvent>(Channel.BUFFERED)
    val events: Flow<ChatRoomListEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            chatRoomListUseCases.getChatRooms()
                .distinctUntilChanged()
                .collectLatest { rooms ->
                    _chatRooms.value = rooms
                    updateUiState()
                }
        }

        viewModelScope.launch {
            chatRoomListUseCases.observeChatGroups()
                .distinctUntilChanged()
                .collectLatest { groups ->
                    _uiState.update { it.copy(chatGroups = groups) }
                    val st = _uiState.value.search
                    if (st.filterType == Search.FilterType.GROUP && groups.none { it.id == st.selectedGroupId }) {
                        updateSearch { it.copy(filterType = Search.FilterType.ALL, selectedGroupId = null) }
                    }
                    updateUiState()
                }
        }

        viewModelScope.launch {
            _uiState
                .map { it.search.query }
                .distinctUntilChanged()
                .debounce(200)
                .collectLatest {
                    updateUiState()
                }
        }

        viewModelScope.launch {
            chatRoomListUseCases.observeChatRoomUnreadTotal().collect { total ->
                _uiState.update { it.copy(unreadTotal = total) }
            }
        }

        viewModelScope.launch {
            chatRoomListUseCases.observeIsFetching().collect { fetching ->
                _uiState.update { it.copy(isRefreshing = fetching) }
            }
        }

        viewModelScope.launch {
            chatRoomListUseCases.observeNewChatRoomPush().collect {
                _events.send(ChatRoomListEvent.NewChatRoomArrived)
            }
        }
    }

    private fun updateSearch(transform: (Search.State) -> Search.State) {
        _uiState.update { it.copy(search = transform(it.search)) }
    }

    private fun updateUiState(isEditMode: Boolean = _uiState.value.isEditMode) {
        val state = _uiState.value
        val query = state.search.query
        val filterType = state.search.filterType
        val rooms = _chatRooms.value

        val typeFilteredRooms = when (filterType) {
            Search.FilterType.UNREAD ->
                rooms.filter { it.hasUnread }
            Search.FilterType.GROUP -> {
                val groupRoomIds = state.chatGroups
                    .firstOrNull { it.id == state.search.selectedGroupId }
                    ?.roomIds?.toHashSet()
                    ?: emptySet()
                rooms.filter { it.id in groupRoomIds }
            }
            else -> rooms
        }

        val displayRooms = if (query.isNotEmpty()) {
            typeFilteredRooms.filter { it.matchesTitle(query) }
        } else {
            typeFilteredRooms
        }

        val kind = when {
            query.isNotEmpty() -> ChatRoomListKind.SEARCH
            filterType == Search.FilterType.UNREAD -> ChatRoomListKind.UNREAD
            else -> ChatRoomListKind.IDLE
        }

        _uiState.update {
            it.copy(
                chatItems = displayRooms,
                kind = kind,
                isEditMode = isEditMode,
                groupsWithUnread = groupsWithUnread(rooms, it.chatGroups),
            )
        }
    }

    /** 안읽은 방을 하나라도 가진 그룹 id. */
    private fun groupsWithUnread(rooms: List<ChatRoom.Item>, groups: List<ChatGroup>): Set<String> {
        val unreadRoomIds = rooms
            .filter { it.hasUnread }
            .mapTo(HashSet()) { it.id }
        return groups.asSequence()
            .filter { group -> group.roomIds.any { it in unreadRoomIds } }
            .mapTo(HashSet()) { it.id }
    }

    /**
     * 고른 대화방을 차례로 나간다. 일부가 실패해도 나머지는 계속하고, 끝나면 선택을 비우고 편집 모드를 끈다.
     * 고른 방이 없으면 편집 모드만 끈다.
     */
    private fun leaveSelectedChatRooms() {
        val roomIds = chatRoomsToLeave(_uiState.value.chatItems, _uiState.value.selectedRoomIds)
        if (roomIds.isEmpty()) {
            onAction(ChatRoomListActions.SetMode(ChatRoomListMode.IDLE))
            return
        }
        viewModelScope.launch {
            var left = 0
            var failed = 0
            roomIds.forEach { roomId ->
                if (chatRoomListUseCases.leaveChatRoom(roomId)) left++ else failed++
            }
            _uiState.update { it.copy(selectedRoomIds = emptySet()) }
            updateUiState(isEditMode = false)
            _events.send(ChatRoomListEvent.ChatRoomsBulkLeft(left = left, failed = failed))
        }
    }

    fun onAction(action: ChatRoomListActions) {
        when (action) {
            is ChatRoomListActions.OnSearchQueryChange -> {
                updateSearch { it.copy(query = action.query) }
            }
            is ChatRoomListActions.OnClearSearch -> {
                updateSearch { it.copy(query = "") }
                updateUiState()
            }
            is ChatRoomListActions.SetMode -> {
                val isEdit = action.mode == ChatRoomListMode.EDIT
                if (!isEdit) {
                    _uiState.update { it.copy(selectedRoomIds = emptySet()) }
                }
                updateUiState(isEditMode = isEdit)
            }
            is ChatRoomListActions.OnSelectRoom -> {
                val roomId = action.item.id
                _uiState.update { state ->
                    val current = state.selectedRoomIds
                    state.copy(selectedRoomIds = if (current.contains(roomId)) current - roomId else current + roomId)
                }
                updateUiState()
            }
            is ChatRoomListActions.OnFilterTypeChange -> {
                updateSearch {
                    it.copy(filterType = action.filterType, selectedGroupId = null)
                }
                updateUiState()
            }
            is ChatRoomListActions.OnGroupFilterChange -> {
                updateSearch {
                    it.copy(filterType = Search.FilterType.GROUP, selectedGroupId = action.groupId)
                }
                updateUiState()
            }
            is ChatRoomListActions.OnAddRoomToGroup -> {
                viewModelScope.launch {
                    runCatching {
                        chatRoomListUseCases.addRoomToGroup(action.groupId, action.chatRoomId)
                    }
                    val groupName = _uiState.value.chatGroups
                        .firstOrNull { it.id == action.groupId }?.name.orEmpty()
                    _events.send(ChatRoomListEvent.ChatRoomAddedToGroup(groupName))
                }
            }
            is ChatRoomListActions.OnRemoveRoomFromGroup -> {
                viewModelScope.launch {
                    runCatching {
                        chatRoomListUseCases.removeRoomFromGroup(action.groupId, action.chatRoomId)
                    }
                    _events.send(ChatRoomListEvent.ChatRoomRemovedFromGroup)
                }
            }
            is ChatRoomListActions.OnRefresh -> {
                if (chatRoomListUseCases.observeIsFetching().value) return
                viewModelScope.launch {
                    runCatching { chatRoomListUseCases.fetchChatRooms() }
                    _events.send(ChatRoomListEvent.ScrollToTop)
                }
            }
            is ChatRoomListActions.LeaveSelectedChatRooms -> leaveSelectedChatRooms()
            is ChatRoomListActions.LeaveChatRoom -> {
                viewModelScope.launch {
                    val ok = chatRoomListUseCases.leaveChatRoom(action.chatRoomId)
                    _events.send(ChatRoomListEvent.ChatRoomLeft(success = ok))
                }
            }
            is ChatRoomListActions.SetChatRoomPin -> {
                viewModelScope.launch {
                    val ok = chatRoomListUseCases.setChatRoomPin(action.chatRoomId, action.pinned)
                    _events.send(
                        ChatRoomListEvent.ChatRoomPinChanged(success = ok, pinned = action.pinned)
                    )
                }
            }
            is ChatRoomListActions.SetChatRoomAlarm -> {
                viewModelScope.launch {
                    val ok = chatRoomListUseCases.setChatRoomAlarm(action.chatRoomId, action.isAlarm)
                    val muted = action.isAlarm == ALARM_OFF
                    _events.send(ChatRoomListEvent.ChatRoomAlarmChanged(success = ok, muted = muted))
                }
            }
            is ChatRoomListActions.RenameChatRoom -> {
                viewModelScope.launch {
                    val newName = action.newName.trim()
                    val current = _chatRooms.value.firstOrNull { it.id == action.chatRoomId }
                        ?: return@launch
                    if (newName == current.title) return@launch

                    when (validateChatRoomName(newName)) {
                        ChatRoomNameValidation.Empty ->
                            _events.send(ChatRoomListEvent.RenameInvalid(ChatRoomNameValidation.Empty))
                        ChatRoomNameValidation.TooLong ->
                            _events.send(ChatRoomListEvent.RenameInvalid(ChatRoomNameValidation.TooLong))
                        ChatRoomNameValidation.Valid -> {
                            val ok = chatRoomListUseCases.renameChatRoom(action.chatRoomId, newName)
                            _events.send(ChatRoomListEvent.ChatRoomRenamed(ok, newName))
                        }
                    }
                }
            }
        }
    }
}

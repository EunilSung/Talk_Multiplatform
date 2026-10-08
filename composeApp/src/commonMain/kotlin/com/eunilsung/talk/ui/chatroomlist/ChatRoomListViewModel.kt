package com.eunilsung.talk.ui.chatroomlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.Search
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases

class ChatRoomListViewModel(
    private val chatRoomListUseCases: ChatRoomListUseCases
) : ViewModel() {

    private val _chatRooms = MutableStateFlow<List<ChatRoom.Item>>(emptyList())
    private val _searchState = MutableStateFlow(Search.State(filterType = Search.FilterType.ALL))
    val searchState = _searchState.asStateFlow()

    private val _uiState = MutableStateFlow<ChatRoomListUiState>(ChatRoomListUiState.Idle())
    val uiState: StateFlow<ChatRoomListUiState> = _uiState.asStateFlow()

    private val _selectedRoomIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedRoomIds: StateFlow<Set<String>> = _selectedRoomIds.asStateFlow()

    private val _events = Channel<ChatRoomListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val isRefreshing: StateFlow<Boolean> = chatRoomListUseCases.observeIsFetching()

    private val _scrollToTop = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTop: SharedFlow<Unit> = _scrollToTop.asSharedFlow()

    val newChatRoomPush: SharedFlow<String> =
        chatRoomListUseCases.observeNewChatRoomPush()

    val unreadTotal: StateFlow<Int> = chatRoomListUseCases.observeChatRoomUnreadTotal()

    /** 전체 그룹 (kind 1/2), 정렬 순. */
    private val _chatGroups = MutableStateFlow<List<ChatGroup>>(emptyList())

    /** 그룹 칩용 — 안읽음·커스텀 모두 SORTID 순으로 노출. */
    val chatGroups: StateFlow<List<ChatGroup>> = _chatGroups.asStateFlow()

    /** 안읽음 방을 하나라도 가진 그룹 id 집합 — 그룹 칩 배지용. */
    val groupsWithUnread: StateFlow<Set<String>> =
        combine(_chatRooms, _chatGroups) { rooms, groups ->
            val unreadRoomIds = rooms
                .filter { (it.unReadCount.toIntOrNull() ?: 0) > 0 }
                .mapTo(HashSet()) { it.id }
            groups.asSequence()
                .filter { group -> group.roomIds.any { it in unreadRoomIds } }
                .mapTo(HashSet()) { it.id }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

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
                    _chatGroups.value = groups
                    val st = _searchState.value
                    if (st.filterType == Search.FilterType.GROUP) {
                        if (groups.none { it.id == st.selectedGroupId }) {
                            _searchState.update {
                                it.copy(filterType = Search.FilterType.ALL, selectedGroupId = null)
                            }
                        }
                        updateUiState()
                    }
                }
        }

        viewModelScope.launch {
            _searchState
                .map { it.query }
                .distinctUntilChanged()
                .debounce(200)
                .collectLatest {
                    updateUiState()
                }
        }
    }

    private fun updateUiState(isEditMode: Boolean = _uiState.value.isEditMode) {
        val query = _searchState.value.query
        val filterType = _searchState.value.filterType
        val selectedIds = _selectedRoomIds.value

        val mappedRooms = _chatRooms.value.map {
            it.copy(isSelect = selectedIds.contains(it.id))
        }

        val typeFilteredRooms = when (filterType) {
            Search.FilterType.UNREAD ->
                mappedRooms.filter { it.unReadCount.isNotEmpty() && it.unReadCount != "0" }
            Search.FilterType.GROUP -> {
                val groupRoomIds = _chatGroups.value
                    .firstOrNull { it.id == _searchState.value.selectedGroupId }
                    ?.roomIds?.toHashSet()
                    ?: emptySet()
                mappedRooms.filter { it.id in groupRoomIds }
            }
            else -> mappedRooms
        }

        val displayRooms = if (query.isNotEmpty()) {
            typeFilteredRooms.filter { room ->
                room.title.contains(query, ignoreCase = true)
            }
        } else {
            typeFilteredRooms
        }

        _uiState.value = if (query.isNotEmpty()) {
            ChatRoomListUiState.Search(chatItems = displayRooms, isEditMode = isEditMode)
        } else if (filterType == Search.FilterType.UNREAD) {
            ChatRoomListUiState.Unread(chatItems = displayRooms, isEditMode = isEditMode)
        } else {
            ChatRoomListUiState.Idle(chatItems = displayRooms, isEditMode = isEditMode)
        }
    }

    /**
     * 고른 대화방을 차례로 나간다. 일부가 실패해도 나머지는 계속하고, 끝나면 선택을 비우고 편집 모드를 끈다.
     * 고른 방이 없으면 편집 모드만 끈다.
     */
    private fun leaveSelectedChatRooms() {
        val roomIds = chatRoomsToLeave(_uiState.value.chatItems, _selectedRoomIds.value)
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
            _selectedRoomIds.value = emptySet()
            updateUiState(isEditMode = false)
            _events.send(ChatRoomListEvent.ChatRoomsBulkLeft(left = left, failed = failed))
        }
    }

    fun onAction(action: ChatRoomListActions) {
        when (action) {
            is ChatRoomListActions.OnSearchQueryChange -> {
                _searchState.update { it.copy(query = action.query) }
            }
            is ChatRoomListActions.OnClearSearch -> {
                _searchState.update { it.copy(query = "") }
                updateUiState()
            }
            is ChatRoomListActions.SetMode -> {
                val isEdit = action.mode == ChatRoomListMode.EDIT
                if (!isEdit) {
                    _selectedRoomIds.value = emptySet()
                }
                updateUiState(isEditMode = isEdit)
            }
            is ChatRoomListActions.OnSelectRoom -> {
                val roomId = action.item.id
                _selectedRoomIds.update { current ->
                    if (current.contains(roomId)) current - roomId else current + roomId
                }
                updateUiState()
            }
            is ChatRoomListActions.OnFilterTypeChange -> {
                _searchState.update {
                    it.copy(filterType = action.filterType, selectedGroupId = null)
                }
                updateUiState()
            }
            is ChatRoomListActions.OnGroupFilterChange -> {
                _searchState.update {
                    it.copy(filterType = Search.FilterType.GROUP, selectedGroupId = action.groupId)
                }
                updateUiState()
            }
            is ChatRoomListActions.OnAddRoomToGroup -> {
                viewModelScope.launch {
                    runCatching {
                        chatRoomListUseCases.addRoomToGroup(action.groupId, action.chatRoomId)
                    }
                    val groupName = _chatGroups.value
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
                if (isRefreshing.value) return
                viewModelScope.launch {
                    runCatching { chatRoomListUseCases.fetchChatRooms() }
                    _scrollToTop.tryEmit(Unit)
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
                    val muted = action.isAlarm == "1"
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

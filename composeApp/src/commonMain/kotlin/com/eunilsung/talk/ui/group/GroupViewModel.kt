package com.eunilsung.talk.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.usecase.GroupUseCases
import com.eunilsung.talk.domain.usecase.InviteUseCases
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.util.GroupNameValidation
import com.eunilsung.talk.domain.util.validateGroupName
import com.eunilsung.talk.domain.util.filterMembers

@OptIn(FlowPreview::class)
class GroupViewModel(
    private val groupUseCases: GroupUseCases,
    private val inviteUseCases: InviteUseCases,
) : ViewModel() {

    private val _groups = MutableStateFlow<List<Group.Item>>(emptyList())

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    private val _events = Channel<GroupEvent>(Channel.BUFFERED)
    val events: Flow<GroupEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            groupUseCases.getGroups()
                .distinctUntilChanged()
                .collectLatest { groups ->
                    _groups.value = groups
                    performSearch(_uiState.value.search.query)
                }
        }

        viewModelScope.launch {
            _uiState
                .map { it.search.query }
                .distinctUntilChanged()
                .debounce(200)
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }

    fun onAction(action: GroupActions) {
        when (action) {
            is GroupActions.ToggleGroup -> toggleGroup(action.groupId)
            is GroupActions.FetchGroups -> {
                viewModelScope.launch {
                    groupUseCases.fetchGroups()
                }
            }
            is GroupActions.OnSearchQueryChange -> {
                _uiState.update { it.copy(search = it.search.copy(query = action.query)) }
            }
            is GroupActions.OnClearSearch -> {
                _uiState.update { it.copy(search = it.search.copy(query = "")) }
            }
            is GroupActions.SetMode -> changeMode(action.mode)
            is GroupActions.OnSelectUser -> toggleUserSelection(action.user)
            is GroupActions.RemoveUserFromGroup -> {
                viewModelScope.launch {
                    val ok = groupUseCases.removeUserFromGroup(action.userId, action.groupId)
                    _events.send(GroupEvent.UserRemoved(ok))
                }
            }
            is GroupActions.MoveUserToGroup -> {
                viewModelScope.launch {
                    val ok = groupUseCases.moveUserToGroup(
                        action.userId,
                        action.fromGroupId,
                        action.toGroupId
                    )
                    _events.send(GroupEvent.UserMoved(ok, action.toGroupId))
                }
            }
            is GroupActions.RenameGroup -> {
                viewModelScope.launch {
                    val newName = action.newName.trim()
                    val currentGroups = _groups.value
                    val target = currentGroups.firstOrNull { it.id == action.groupId }
                        ?: return@launch
                    if (newName == target.name) return@launch

                    val existingNames = currentGroups
                        .asSequence()
                        .filter { it.id != action.groupId }
                        .map { it.name }
                        .toSet()
                    val validation = validateGroupName(newName, existingNames)
                    if (validation != GroupNameValidation.Valid) {
                        _events.send(GroupEvent.RenameInvalid(validation))
                        return@launch
                    }

                    val ok = groupUseCases.renameGroup(action.groupId, newName)
                    _events.send(GroupEvent.GroupRenamed(ok, newName))
                }
            }
            is GroupActions.DeleteGroup -> {
                viewModelScope.launch {
                    val ok = groupUseCases.deleteGroup(action.groupId)
                    _events.send(GroupEvent.GroupDeleted(ok))
                }
            }
            is GroupActions.CreateGroup -> {
                viewModelScope.launch {
                    val newName = action.name.trim()
                    val existingNames = _groups.value.map { it.name }.toSet()
                    val validation = validateGroupName(newName, existingNames)
                    when (validation) {
                        GroupNameValidation.Empty -> {
                            _events.send(GroupEvent.CreateInvalid(validation))
                        }
                        GroupNameValidation.TooLong,
                        GroupNameValidation.InvalidChars,
                        GroupNameValidation.Duplicate -> Unit
                        GroupNameValidation.Valid -> {
                            val ok = groupUseCases.createGroup(newName)
                            _events.send(GroupEvent.GroupCreated(ok, newName))
                        }
                    }
                }
            }
            is GroupActions.StartChat -> startChat(action.users)
        }
    }

    /** 새 방을 만들어 들어가고 편집 모드를 끈다. 만들지 못하면 편집 모드와 선택은 그대로 둔다. */
    private fun startChat(users: List<Pair<String, String>>) {
        viewModelScope.launch {
            val roomId = runCatching { inviteUseCases.createChatRoom(users) }.getOrNull()
            if (roomId.isNullOrBlank()) {
                _events.send(GroupEvent.ChatStartFailed)
                return@launch
            }
            changeMode(GroupMode.IDLE)
            _events.send(GroupEvent.ChatStarted(roomId))
        }
    }

    private fun toggleUserSelection(user: User) {
        _uiState.update { state ->
            val current = state.selectedUserIds
            state.copy(selectedUserIds = if (user.id in current) current - user.id else current + user.id)
        }
    }

    private fun changeMode(mode: GroupMode) {
        val isEdit = mode == GroupMode.EDIT
        _uiState.update {
            it.copy(
                isEditMode = isEdit,
                selectedUserIds = if (isEdit) it.selectedUserIds else emptySet(),
            )
        }
    }

    /** 검색어로 그룹을 거른다. 검색어가 없으면 전부 보인다. */
    private fun performSearch(query: String) {
        _uiState.update { it.copy(items = _groups.value.filterMembers(query)) }
    }

    private fun toggleGroup(groupId: String) {
        val group = _uiState.value.items.firstOrNull { it.id == groupId } ?: return
        val newIsExpanded = !group.isExpanded
        groupUseCases.toggleGroupExpanded(group.id, newIsExpanded)

        val updatedAllGroups = _groups.value.map {
            if (it.id == group.id) it.copy(isExpanded = newIsExpanded) else it
        }
        _groups.value = updatedAllGroups
        performSearch(_uiState.value.search.query)
    }
}

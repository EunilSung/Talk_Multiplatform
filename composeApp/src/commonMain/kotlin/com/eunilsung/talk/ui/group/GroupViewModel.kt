package com.eunilsung.talk.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.usecase.GroupUseCases
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.Search
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.util.GroupNameValidation
import com.eunilsung.talk.domain.util.validateGroupName

@OptIn(FlowPreview::class)
class GroupViewModel(
    private val groupUseCases: GroupUseCases,
) : ViewModel() {

    private val _groups = MutableStateFlow<List<Group.Item>>(emptyList())

    private val _searchState = MutableStateFlow(Search.State())
    val searchState = _searchState.asStateFlow()
    
    private val _uiState = MutableStateFlow<GroupUiState>(GroupUiState.Idle())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    private val _selectedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedUserIds: StateFlow<Set<String>> = _selectedUserIds.asStateFlow()

    private val _events = Channel<GroupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            groupUseCases.getGroups()
                .distinctUntilChanged()
                .collectLatest { groups ->
                    _groups.value = groups
                    updateStateWithGroups(groups)
                }
        }

        viewModelScope.launch {
            _searchState
                .map { it.query }
                .distinctUntilChanged()
                .debounce(200)
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }

    private fun updateStateWithGroups(items: List<Group.Item>) {
        when (val current = _uiState.value) {
            is GroupUiState.Idle -> _uiState.value = current.copy(items = items)
            is GroupUiState.Search -> {
                performSearch(_searchState.value.query)
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
                _searchState.update { it.copy(query = action.query) }
            }
            is GroupActions.OnClearSearch -> {
                _searchState.update { it.copy(query = "") }
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
        }
    }

    private fun toggleUserSelection(user: User) {
        val currentSelected = _selectedUserIds.value.toMutableSet()
        if (currentSelected.contains(user.id)) {
            currentSelected.remove(user.id)
        } else {
            currentSelected.add(user.id)
        }
        _selectedUserIds.value = currentSelected
    }

    private fun changeMode(mode: GroupMode) {
        val isEdit = mode == GroupMode.EDIT
        val current = _uiState.value
        
        if (!isEdit) {
            _selectedUserIds.value = emptySet()
        }

        _uiState.value = when (current) {
            is GroupUiState.Idle -> current.copy(isEditMode = isEdit)
            is GroupUiState.Search -> current.copy(isEditMode = isEdit)
        }
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            val current = _uiState.value
            _uiState.value = GroupUiState.Search(
                items = _groups.value,
                isEditMode = current.isEditMode
            )
            return
        }

        val filteredGroups = _groups.value.mapNotNull { group ->
            val matchingUsers = group.userData.filter { user ->
                com.eunilsung.talk.util.UserSearch.matches(user.userName, user.phoneNumber, query)
            }
            if (matchingUsers.isNotEmpty()) {
                group.copy(userData = matchingUsers, isExpanded = true)
            } else null
        }

        val current = _uiState.value
        _uiState.value = GroupUiState.Search(
            items = filteredGroups,
            isEditMode = current.isEditMode
        )
    }

    private fun toggleGroup(groupId: String) {
        val group = _uiState.value.items.firstOrNull { it.id == groupId } ?: return
        val newIsExpanded = !group.isExpanded
        groupUseCases.toggleGroupExpanded(group.id, newIsExpanded)

        val updatedAllGroups = _groups.value.map {
            if (it.id == group.id) it.copy(isExpanded = newIsExpanded) else it
        }
        _groups.value = updatedAllGroups
        updateStateWithGroups(updatedAllGroups)
    }
}

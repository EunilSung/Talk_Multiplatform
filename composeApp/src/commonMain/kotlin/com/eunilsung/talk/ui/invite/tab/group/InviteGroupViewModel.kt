package com.eunilsung.talk.ui.invite.tab.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.usecase.GroupUseCases

@OptIn(FlowPreview::class)
class InviteGroupViewModel(
    private val groupUseCases: GroupUseCases,
) : ViewModel() {

    private val _expandedOverride = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private val _groups = MutableStateFlow<List<Group.Item>>(emptyList())

    private val _uiState = MutableStateFlow(InviteGroupUiState())
    val uiState: StateFlow<InviteGroupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            groupUseCases.getGroups()
                .distinctUntilChanged()
                .collectLatest { groups ->
                    _groups.value = groups
                    refresh()
                }
        }

        viewModelScope.launch {
            _uiState
                .map { it.search.query }
                .distinctUntilChanged()
                .debounce(200)
                .collectLatest { _ -> refresh() }
        }

        viewModelScope.launch {
            _expandedOverride.collectLatest { refresh() }
        }
    }

    fun onAction(action: InviteGroupActions) {
        when (action) {
            is InviteGroupActions.ToggleGroup -> toggleGroup(action.groupId)
            is InviteGroupActions.OnSearchQueryChange -> {
                _uiState.update { it.copy(search = it.search.copy(query = action.query)) }
            }
            is InviteGroupActions.OnClearSearch -> {
                _uiState.update { it.copy(search = it.search.copy(query = "")) }
            }
        }
    }

    private fun toggleGroup(groupId: String) {
        val current = _expandedOverride.value
        val target = applyExpandedOverride(_groups.value).firstOrNull { it.id == groupId } ?: return
        _expandedOverride.value = current + (groupId to !target.isExpanded)
    }

    private fun applyExpandedOverride(source: List<Group.Item>): List<Group.Item> {
        val map = _expandedOverride.value
        return source.map { g ->
            map[g.id]?.let { ov -> g.copy(isExpanded = ov) } ?: g
        }
    }

    private fun refresh() {
        val query = _uiState.value.search.query
        val base = applyExpandedOverride(_groups.value)
        val items = if (query.isBlank()) {
            base
        } else {
            val filtered = base.mapNotNull { group ->
                val matchingUsers = group.userData.filter { user ->
                    user.userName?.contains(query, ignoreCase = true) == true
                }
                if (matchingUsers.isNotEmpty()) group.copy(userData = matchingUsers, isExpanded = true)
                else null
            }
            filtered
        }
        _uiState.update { it.copy(items = items) }
    }
}

package com.eunilsung.talk.ui.userprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.repository.UserProfileRepository

class UserProfileViewModel(
    private val repository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UserProfileUiState>(UserProfileUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var currentUserId: String = ""
    private var loadJob: Job? = null

    fun onAction(action: UserProfileActions) {
        when (action) {
            is UserProfileActions.Load -> load(action.userId)
            is UserProfileActions.Retry -> if (currentUserId.isNotBlank()) load(currentUserId)
            else -> Unit
        }
    }

    private fun load(userId: String) {
        currentUserId = userId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cached = runCatching { repository.getCachedProfile(userId) }.getOrNull()
            _uiState.value = if (cached != null) {
                UserProfileUiState.Success(profile = cached, isStale = true)
            } else {
                UserProfileUiState.Loading
            }

            repository.fetchProfile(userId)
                .onSuccess { fresh ->
                    _uiState.value = UserProfileUiState.Success(profile = fresh, isStale = false)
                }
                .onFailure { err ->
                    _uiState.value = if (cached != null) {
                        UserProfileUiState.Success(profile = cached, isStale = true)
                    } else {
                        UserProfileUiState.Error(err.message)
                    }
                }
        }
    }

    fun reset() {
        loadJob?.cancel()
        currentUserId = ""
        _uiState.value = UserProfileUiState.Idle
    }
}

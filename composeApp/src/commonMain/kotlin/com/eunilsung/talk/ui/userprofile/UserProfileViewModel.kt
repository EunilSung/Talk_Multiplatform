package com.eunilsung.talk.ui.userprofile

import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.usecase.UserProfileUseCases

class UserProfileViewModel(
    private val useCases: UserProfileUseCases
) : ViewModel() {

    private val _uiState = MutableStateFlow<UserProfileUiState>(UserProfileUiState.Idle)
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

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
        val isMe = userId.isNotBlank() && userId.equals(useCases.getMyUser().id, ignoreCase = true)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cached = runCatching { useCases.getCachedProfile(userId) }.getOrNull()
            _uiState.value = if (cached != null) {
                UserProfileUiState.Success(profile = cached, isStale = true, isMe = isMe)
            } else {
                UserProfileUiState.Loading
            }

            useCases.fetchProfile(userId)
                .onSuccess { fresh ->
                    _uiState.value = UserProfileUiState.Success(profile = fresh, isStale = false, isMe = isMe)
                }
                .onFailure {
                    _uiState.value = if (cached != null) {
                        UserProfileUiState.Success(profile = cached, isStale = true, isMe = isMe)
                    } else {
                        UserProfileUiState.Error
                    }
                }
        }
    }
}

package com.eunilsung.talk.ui.userprofile

import com.eunilsung.talk.domain.model.UserProfile

sealed interface UserProfileUiState {
    data object Idle : UserProfileUiState
    data object Loading : UserProfileUiState

    data class Success(
        val profile: UserProfile,
        val isStale: Boolean = false
    ) : UserProfileUiState

    data class Error(val message: String) : UserProfileUiState
}

package com.eunilsung.talk.ui.userprofile

import com.eunilsung.talk.domain.model.UserProfile

sealed interface UserProfileUiState {
    data object Idle : UserProfileUiState
    data object Loading : UserProfileUiState

    /** [isMe] 면 프로필 사진 위에 사진 바꾸기 버튼을 얹는다. */
    data class Success(
        val profile: UserProfile,
        val isStale: Boolean = false,
        val isMe: Boolean = false,
    ) : UserProfileUiState

    /** [message] 가 null 이면 화면이 `profile_load_failed` 문구를 보인다. */
    data class Error(val message: String? = null) : UserProfileUiState
}

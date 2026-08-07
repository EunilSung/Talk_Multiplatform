package com.eunilsung.talk.ui.userprofile

sealed interface UserProfileActions {
    data class Load(val userId: String) : UserProfileActions
    data object Retry : UserProfileActions
    data object OnClose : UserProfileActions

    data object OnChat : UserProfileActions
    data object OnCall : UserProfileActions
    data object OnSaveContact : UserProfileActions
    data object OnAddToGroup : UserProfileActions
}

package com.eunilsung.talk.ui.login

import com.eunilsung.talk.domain.model.Login

sealed interface LoginActions {
    data class OnIdChange(val id: String) : LoginActions
    data class OnPwChange(val pw: String) : LoginActions
    data class OnToggleSavePw(val isSave: Boolean) : LoginActions
    data class OnLoginClick(val input: Login.LoginRequest) : LoginActions
    data class OnDuplicateLoginConfirm(val input: Login.LoginRequest) : LoginActions
    data object OnResetState : LoginActions
}

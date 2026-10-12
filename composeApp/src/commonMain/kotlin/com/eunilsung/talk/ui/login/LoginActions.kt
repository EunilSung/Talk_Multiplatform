package com.eunilsung.talk.ui.login

import com.eunilsung.talk.domain.model.Login

sealed interface LoginActions {
    /**
     * 로그인 화면이 떴다. 버전을 확인하고, 업데이트가 필요 없으면 기억해 둔 계정으로 바로 로그인한다.
     * 기억해 둔 계정이 없으면 잠시 뒤 입력칸을 보인다.
     */
    data object OnStart : LoginActions
    data class OnIdChange(val id: String) : LoginActions
    data class OnPwChange(val pw: String) : LoginActions
    data class OnToggleSavePw(val isSave: Boolean) : LoginActions
    data class OnLoginClick(val input: Login.LoginRequest) : LoginActions
    data class OnDuplicateLoginConfirm(val input: Login.LoginRequest) : LoginActions
    data object OnResetState : LoginActions
}

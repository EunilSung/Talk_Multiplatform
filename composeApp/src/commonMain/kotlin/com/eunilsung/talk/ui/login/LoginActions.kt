package com.eunilsung.talk.ui.login

import com.eunilsung.talk.domain.model.Login

sealed interface LoginActions {
    /**
     * 로그인 화면이 떴다. 버전을 확인하고, 업데이트가 필요 없으면 자동 로그인을 켠 경우 남은 로그인(서버 모드는 토큰)으로
     * 바로 들어간다. 남은 로그인이 없으면 잠시 뒤 입력칸을 보인다.
     */
    data object OnStart : LoginActions
    data class OnIdChange(val id: String) : LoginActions
    data class OnPwChange(val pw: String) : LoginActions
    data class OnToggleSavePw(val isSave: Boolean) : LoginActions
    data object OnTogglePasswordVisible : LoginActions
    data class OnLoginClick(val input: Login.LoginRequest) : LoginActions
    data class OnDuplicateLoginConfirm(val input: Login.LoginRequest) : LoginActions
    data object OnResetState : LoginActions

    /**
     * 남은 로그인으로 들어가지 못한 뒤([LoginStatus.ResumeFailed]) '다시 시도'를 눌렀다. 같은 토큰으로 다시 묻는다 —
     * 비밀번호를 다시 넣지 않아도 되고, 새 토큰을 받아 옛 토큰을 버리지도 않는다.
     */
    data object OnRetryResume : LoginActions
}

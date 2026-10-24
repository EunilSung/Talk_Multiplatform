package com.eunilsung.talk.ui.login

import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.domain.model.VersionInfo

/** 로그인 화면의 상태. */
data class LoginUiState(
    val status: LoginStatus = LoginStatus.Loading,
    /** 입력 중인 아이디·비밀번호와 비밀번호 저장 여부. */
    val input: Login.LoginRequest = Login.LoginRequest(),
    /** 버전 확인 결과. 확인 전이거나 확인하지 못했으면 null. */
    val versionInfo: VersionInfo? = null,
    /** 비밀번호 칸의 글자를 그대로 보일지. */
    val isPasswordVisible: Boolean = false,
) {
    /** 아이디와 비밀번호를 다 넣었고 로그인 중이 아니면 로그인 버튼을 누를 수 있다. */
    val canSubmit: Boolean
        get() = input.id.isNotBlank() && input.pw.isNotBlank() && status !is LoginStatus.Loading
}

/** 로그인 진행 단계. */
sealed interface LoginStatus {
    data object Idle : LoginStatus
    data object Loading : LoginStatus
    data object Success : LoginStatus
    data class Error(val message: String) : LoginStatus
    data class Duplicate(val message: String) : LoginStatus

    /**
     * 남은 로그인(서버 모드는 저장된 토큰)으로 다시 들어가지 못했다 — 서버 모드에서는 서버에 닿지 못한 경우다.
     * 토큰은 그대로라 '다시 시도'([LoginActions.OnRetryResume])로 비밀번호 없이 다시 묻는다. 취소하면 입력칸이다.
     */
    data class ResumeFailed(val message: String) : LoginStatus
}

fun Login.LoginResult.toStatus(): LoginStatus = when (this) {
    is Login.LoginResult.Success -> LoginStatus.Success
    is Login.LoginResult.Error -> LoginStatus.Error(message)
    is Login.LoginResult.Duplicate -> LoginStatus.Duplicate(message)
}

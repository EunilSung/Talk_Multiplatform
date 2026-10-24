package com.eunilsung.talk.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.domain.usecase.LoginUseCases
import com.eunilsung.talk.domain.usecase.VersionCheckUseCase

/**
 * 로그인 화면.
 *
 * 저장해 둔 비밀번호를 입력칸에 채우지 않는다 — 채우면 '비밀번호 보기'로 누구나 읽을 수 있다. 자동 로그인은
 * [LoginUseCases.resumeLogin] 이 입력칸을 거치지 않고 한다. '보기'는 사용자가 이번에 직접 넣은 글자만 보인다.
 * 자동 로그인이 서버에 닿지 못하면 [LoginStatus.ResumeFailed] 로 '다시 시도'를 묻는다.
 */
class LoginViewModel(
    private val loginUseCases: LoginUseCases,
    private val versionCheck: VersionCheckUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState(input = loginUseCases.getSavedLogin()))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loginUseCases.observeLoginState()
                .drop(1)
                .filter { !it }
                .collect {
                    _uiState.update {
                        it.copy(status = LoginStatus.Idle, input = it.input.copy(pw = ""), isPasswordVisible = false)
                    }
                }
        }
    }

    fun onAction(action: LoginActions) {
        when (action) {
            is LoginActions.OnStart -> start()
            is LoginActions.OnIdChange -> updateInput { it.copy(id = action.id.trim()) }
            is LoginActions.OnPwChange -> updateInput { it.copy(pw = action.pw.trim()) }
            is LoginActions.OnToggleSavePw -> toggleSavePw(action.isSave)
            LoginActions.OnTogglePasswordVisible -> _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            is LoginActions.OnLoginClick -> performLogin(action.input)
            is LoginActions.OnDuplicateLoginConfirm -> duplicateLogin(action.input)
            LoginActions.OnResetState -> resetUiState()
            LoginActions.OnRetryResume -> retryResume()
        }
    }

    private fun start() {
        viewModelScope.launch {
            val info = runCatching { versionCheck() }.getOrNull()
            _uiState.update { it.copy(versionInfo = info) }
            if (info?.needsUpdate == true) return@launch

            if (!resume()) {
                delay(1000)
                resetUiState()
            }
        }
    }

    private fun retryResume() {
        viewModelScope.launch {
            setStatus(LoginStatus.Loading)
            if (!resume()) resetUiState()
        }
    }

    /**
     * 남은 로그인으로 들어가 본다. 남은 로그인이 없으면 false 다.
     * 들어가지 못하면 오류 대신 [LoginStatus.ResumeFailed] 로 두어 화면이 '다시 시도'를 묻게 한다.
     */
    private suspend fun resume(): Boolean {
        val resumed = loginUseCases.resumeLogin() ?: return false
        setStatus(if (resumed is Login.LoginResult.Error) LoginStatus.ResumeFailed(resumed.message) else resumed.toStatus())
        return true
    }

    private fun performLogin(input: Login.LoginRequest) {
        viewModelScope.launch {
            setStatus(LoginStatus.Loading)
            setStatus(loginUseCases.login(input).toStatus())
        }
    }

    private fun toggleSavePw(isSave: Boolean) {
        loginUseCases.setSavePassword(isSave)
        updateInput { it.copy(isSavePw = isSave) }
    }

    private fun duplicateLogin(input: Login.LoginRequest) {
        viewModelScope.launch {
            setStatus(LoginStatus.Loading)
            setStatus(loginUseCases.duplicateLogin(input).toStatus())
        }
    }

    private fun resetUiState() {
        setStatus(LoginStatus.Idle)
    }

    private fun setStatus(status: LoginStatus) {
        _uiState.update { it.copy(status = status) }
    }

    private fun updateInput(transform: (Login.LoginRequest) -> Login.LoginRequest) {
        _uiState.update { it.copy(input = transform(it.input)) }
    }
}

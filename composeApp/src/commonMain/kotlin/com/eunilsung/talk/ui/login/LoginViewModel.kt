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
import com.eunilsung.talk.domain.model.VersionInfo
import com.eunilsung.talk.domain.usecase.LoginUseCases
import com.eunilsung.talk.domain.usecase.VersionCheckUseCase

class LoginViewModel(
    private val loginUseCases: LoginUseCases,
    private val versionCheck: VersionCheckUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _inputState = MutableStateFlow(loginUseCases.getSavedLogin())
    val inputState: StateFlow<Login.LoginRequest> = _inputState.asStateFlow()

    private val _versionInfo = MutableStateFlow<VersionInfo?>(null)

    /** 버전 확인 결과. 확인 전이거나 확인하지 못했으면 null. */
    val versionInfo: StateFlow<VersionInfo?> = _versionInfo.asStateFlow()


    init {
        viewModelScope.launch {
            loginUseCases.observeLoginState()
                .drop(1)
                .filter { !it }
                .collect {
                    _inputState.update { it.copy(pw = loginUseCases.getSavedLogin().pw) }
                    _uiState.value = LoginUiState.Idle
                }
        }
    }

    fun onAction(action: LoginActions) {
        when (action) {
            is LoginActions.OnStart -> start()
            is LoginActions.OnIdChange -> _inputState.update { it.copy(id = action.id.trim()) }
            is LoginActions.OnPwChange -> _inputState.update { it.copy(pw = action.pw.trim()) }
            is LoginActions.OnToggleSavePw -> toggleSavePw(action.isSave)
            is LoginActions.OnLoginClick -> performLogin(action.input)
            is LoginActions.OnDuplicateLoginConfirm -> duplicateLogin(action.input)
            LoginActions.OnResetState -> resetUiState()
        }
    }

    private fun start() {
        viewModelScope.launch {
            val info = runCatching { versionCheck() }.getOrNull()
            _versionInfo.value = info
            if (info?.needsUpdate == true) return@launch

            val input = _inputState.value
            if (input.id.isNotEmpty() && input.pw.isNotEmpty()) {
                performLogin(input)
            } else {
                delay(1000)
                resetUiState()
            }
        }
    }

    private fun performLogin(input: Login.LoginRequest) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            _uiState.value = loginUseCases.login(input).toUiState()
        }
    }

    private fun toggleSavePw(isSave: Boolean) {
        loginUseCases.setSavePassword(isSave)
        _inputState.update { it.copy(isSavePw = isSave) }
    }

    private fun duplicateLogin(input: Login.LoginRequest) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            _uiState.value = loginUseCases.duplicateLogin(input).toUiState()
        }
    }

    fun resetUiState() {
        _uiState.value = LoginUiState.Idle
    }
}

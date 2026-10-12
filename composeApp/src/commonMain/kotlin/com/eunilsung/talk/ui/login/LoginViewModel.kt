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
                        it.copy(status = LoginStatus.Idle, input = it.input.copy(pw = loginUseCases.getSavedLogin().pw))
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
            is LoginActions.OnLoginClick -> performLogin(action.input)
            is LoginActions.OnDuplicateLoginConfirm -> duplicateLogin(action.input)
            LoginActions.OnResetState -> resetUiState()
        }
    }

    private fun start() {
        viewModelScope.launch {
            val info = runCatching { versionCheck() }.getOrNull()
            _uiState.update { it.copy(versionInfo = info) }
            if (info?.needsUpdate == true) return@launch

            val input = _uiState.value.input
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

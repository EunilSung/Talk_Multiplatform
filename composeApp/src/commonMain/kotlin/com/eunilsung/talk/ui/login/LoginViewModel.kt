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
import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.domain.usecase.LoginUseCases

class LoginViewModel(
    private val loginUseCases: LoginUseCases,
    private val loginRepository: LoginRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _inputState = MutableStateFlow(
        Login.LoginRequest(
            id = loginRepository.getSavedId() ?: "",
            pw = loginRepository.getSavedPw() ?: "",
            isSavePw = loginRepository.isSavePw()
        )
    )
    val inputState: StateFlow<Login.LoginRequest> = _inputState.asStateFlow()


    init {
        viewModelScope.launch {
            loginRepository.isLoggedIn
                .drop(1)
                .filter { !it }
                .collect {
                    _inputState.update { it.copy(pw = loginRepository.getSavedPw() ?: "") }
                    _uiState.value = LoginUiState.Idle
                }
        }
    }

    fun onAction(action: LoginActions) {
        when (action) {
            is LoginActions.OnIdChange -> _inputState.update { it.copy(id = action.id.trim()) }
            is LoginActions.OnPwChange -> _inputState.update { it.copy(pw = action.pw.trim()) }
            is LoginActions.OnToggleSavePw -> toggleSavePw(action.isSave)
            is LoginActions.OnLoginClick -> performLogin(action.input)
            is LoginActions.OnDuplicateLoginConfirm -> duplicateLogin(action.input)
            LoginActions.OnResetState -> resetUiState()
        }
    }

    private fun performLogin(input: Login.LoginRequest) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            _uiState.value = loginUseCases.login(input).toUiState()
        }
    }

    private fun toggleSavePw(isSave: Boolean) {
        loginRepository.setSavePw(isSave)
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

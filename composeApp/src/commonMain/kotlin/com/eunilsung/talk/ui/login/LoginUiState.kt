package com.eunilsung.talk.ui.login

import com.eunilsung.talk.domain.model.Login

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
    data class Duplicate(val message: String) : LoginUiState
}

fun Login.LoginResult.toUiState(): LoginUiState = when (this) {
    is Login.LoginResult.Success -> LoginUiState.Success
    is Login.LoginResult.Error -> LoginUiState.Error(message)
    is Login.LoginResult.Duplicate -> LoginUiState.Duplicate(message)
}

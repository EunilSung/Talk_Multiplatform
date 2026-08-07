package com.eunilsung.talk.domain.repository

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.domain.model.Login

interface LoginRepository {
    val isLoggedIn: StateFlow<Boolean>
    val isConnected: StateFlow<Boolean>
    val isForeground: MutableStateFlow<Boolean>
    fun logout()
    suspend fun requestLogin(input: Login.LoginRequest): Login.LoginResult
    fun getSavedId(): String?
    fun getSavedPw(): String?
    fun isSavePw(): Boolean
    fun setSavePw(isSave: Boolean)
    fun checkConnectionAndRelogin()
    suspend fun duplicateLogin(input: Login.LoginRequest): Login.LoginResult
}
package com.eunilsung.talk.domain.repository

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.domain.model.User

interface LoginRepository {
    val isLoggedIn: StateFlow<Boolean>
    val isConnected: StateFlow<Boolean>
    /** 앱이 화면 앞에 있는지. */
    val isForeground: StateFlow<Boolean>
    fun setForeground(isForeground: Boolean)
    fun logout()
    suspend fun requestLogin(input: Login.LoginRequest): Login.LoginResult
    fun getSavedId(): String?
    fun getSavedPw(): String?
    fun isSavePw(): Boolean
    fun setSavePw(isSave: Boolean)
    fun checkConnectionAndRelogin()
    suspend fun duplicateLogin(input: Login.LoginRequest): Login.LoginResult

    /** 로그인한 나(아이디·이름). 로그인 전이면 빈 값이다. */
    fun getMyUser(): User
}
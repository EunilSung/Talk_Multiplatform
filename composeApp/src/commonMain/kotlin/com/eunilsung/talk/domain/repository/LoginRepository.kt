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

    /** 자동 로그인을 켰는지. 켜면 다음에 앱을 열 때 [resumeLogin] 이 입력 없이 들어간다. */
    fun isSavePw(): Boolean
    fun setSavePw(isSave: Boolean)
    fun checkConnectionAndRelogin()
    suspend fun duplicateLogin(input: Login.LoginRequest): Login.LoginResult

    /**
     * 기기에 남은 로그인으로 입력 없이 다시 들어간다. 자동 로그인을 켜지 않았거나 남은 로그인이 없으면 null.
     *
     * 저장한 비밀번호를 화면에 돌려주지 않는다 — 입력칸에 채우면 '보기'로 누구나 읽을 수 있기 때문이다.
     */
    suspend fun resumeLogin(): Login.LoginResult?

    /** 로그인한 나(아이디·이름). 로그인 전이면 빈 값이다. */
    fun getMyUser(): User
}
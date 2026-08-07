package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.domain.repository.LoginRepository

/** 로그인 관련 유스케이스 번들. */
data class LoginUseCases(
    val login: LoginUseCase,
    val logout: LogoutUseCase,
    val duplicateLogin: DuplicateLoginUseCase
)

/** 로그인 요청 유스케이스. */
class LoginUseCase(
    private val loginRepository: LoginRepository
) {
    suspend operator fun invoke(input: Login.LoginRequest): Login.LoginResult {
        return try {
            loginRepository.requestLogin(input)
        } catch (e: Exception) {
            Login.LoginResult.Error(e.message ?: "Unknown error")
        }
    }
}

/** 중복 로그인 요청 유스케이스. */
class DuplicateLoginUseCase(
    private val loginRepository: LoginRepository
) {
    suspend operator fun invoke(input: Login.LoginRequest): Login.LoginResult {
        return try {
            loginRepository.duplicateLogin(input)
        } catch (e: Exception) {
            Login.LoginResult.Error(e.message ?: "Unknown error")
        }
    }
}

/** 로그아웃 유스케이스. */
class LogoutUseCase(
    private val loginRepository: LoginRepository
) {
    operator fun invoke() {
        loginRepository.logout()
    }
}

package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.repository.LoginRepository
import kotlinx.coroutines.flow.StateFlow

/** 로그인 관련 유스케이스 번들. */
data class LoginUseCases(
    val login: LoginUseCase,
    val logout: LogoutUseCase,
    val duplicateLogin: DuplicateLoginUseCase,
    val observeLoginState: ObserveLoginStateUseCase,
    val getSavedLogin: GetSavedLoginUseCase,
    val setSavePassword: SetSavePasswordUseCase,
    val getMyUser: GetMyUserUseCase,
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

/** 로그인 여부 구독. */
class ObserveLoginStateUseCase(private val loginRepository: LoginRepository) {
    operator fun invoke(): StateFlow<Boolean> = loginRepository.isLoggedIn
}

/** 기기에 기억해 둔 아이디·비밀번호·저장 여부. 없으면 빈 값. */
class GetSavedLoginUseCase(private val loginRepository: LoginRepository) {
    operator fun invoke(): Login.LoginRequest = Login.LoginRequest(
        id = loginRepository.getSavedId().orEmpty(),
        pw = loginRepository.getSavedPw().orEmpty(),
        isSavePw = loginRepository.isSavePw(),
    )
}

/** 비밀번호를 기억할지. */
class SetSavePasswordUseCase(private val loginRepository: LoginRepository) {
    operator fun invoke(isSave: Boolean) = loginRepository.setSavePw(isSave)
}

/** 로그인한 나. 화면과 ViewModel 은 설정값 대신 이것으로 내 아이디·이름을 안다. 로그인 전이면 빈 값이다. */
class GetMyUserUseCase(private val loginRepository: LoginRepository) {
    operator fun invoke(): User = loginRepository.getMyUser()
}

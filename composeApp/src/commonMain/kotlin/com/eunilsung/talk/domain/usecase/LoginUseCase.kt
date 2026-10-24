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
    val resumeLogin: ResumeLoginUseCase,
)

/** 로그인 요청 유스케이스. */
class LoginUseCase(
    private val loginRepository: LoginRepository
) {
    suspend operator fun invoke(input: Login.LoginRequest): Login.LoginResult {
        return try {
            loginRepository.requestLogin(input)
        } catch (e: Exception) {
            Login.LoginResult.Error("")
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
            Login.LoginResult.Error("")
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

/** 기기에 기억해 둔 아이디와 자동 로그인 여부. 비밀번호는 입력칸에 채우지 않아 늘 빈 값이다. */
class GetSavedLoginUseCase(private val loginRepository: LoginRepository) {
    operator fun invoke(): Login.LoginRequest = Login.LoginRequest(
        id = loginRepository.getSavedId().orEmpty(),
        isSavePw = loginRepository.isSavePw(),
    )
}

/** 기기에 남은 로그인으로 입력 없이 다시 들어간다. 남은 것이 없거나 확인하다 실패하면 null. */
class ResumeLoginUseCase(private val loginRepository: LoginRepository) {
    suspend operator fun invoke(): Login.LoginResult? {
        return try {
            loginRepository.resumeLogin()
        } catch (e: Exception) {
            null
        }
    }
}

/** 자동 로그인을 할지. */
class SetSavePasswordUseCase(private val loginRepository: LoginRepository) {
    operator fun invoke(isSave: Boolean) = loginRepository.setSavePw(isSave)
}

/** 로그인한 나. 화면과 ViewModel 은 설정값 대신 이것으로 내 아이디·이름을 안다. 로그인 전이면 빈 값이다. */
class GetMyUserUseCase(private val loginRepository: LoginRepository) {
    operator fun invoke(): User = loginRepository.getMyUser()
}

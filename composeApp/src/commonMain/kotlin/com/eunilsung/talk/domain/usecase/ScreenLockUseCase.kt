package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.ScreenLockConfig
import com.eunilsung.talk.domain.repository.BiometricAuthenticator
import com.eunilsung.talk.domain.model.BiometricResult
import com.eunilsung.talk.domain.repository.ScreenLockRepository
import kotlinx.coroutines.flow.StateFlow

/** 화면 잠금의 유스케이스 묶음. */
data class ScreenLockUseCases(
    val observeConfig: ObserveScreenLockConfigUseCase,
    val disable: DisableScreenLockUseCase,
    val setPassword: SetScreenLockPasswordUseCase,
    val setPattern: SetScreenLockPatternUseCase,
    val verifyPassword: VerifyScreenLockPasswordUseCase,
    val verifyPattern: VerifyScreenLockPatternUseCase,
    val setPatternVisible: SetScreenLockPatternVisibleUseCase,
    val setBiometricEnabled: SetScreenLockBiometricUseCase,
    val clearBiometric: ClearScreenLockBiometricUseCase,
    val isBiometricAvailable: IsBiometricAvailableUseCase,
    val authenticateBiometric: AuthenticateBiometricUseCase,
)

/** 화면 잠금 설정 구독. */
class ObserveScreenLockConfigUseCase(private val repository: ScreenLockRepository) {
    operator fun invoke(): StateFlow<ScreenLockConfig> = repository.config
}

/** 화면 잠금 끄기. */
class DisableScreenLockUseCase(private val repository: ScreenLockRepository) {
    operator fun invoke() = repository.disable()
}

/** 비밀번호로 잠그기. */
class SetScreenLockPasswordUseCase(private val repository: ScreenLockRepository) {
    operator fun invoke(pin: String) = repository.setPassword(pin)
}

/** 패턴으로 잠그기. */
class SetScreenLockPatternUseCase(private val repository: ScreenLockRepository) {
    operator fun invoke(dots: List<Int>) = repository.setPattern(dots)
}

/** 비밀번호가 맞는지. */
class VerifyScreenLockPasswordUseCase(private val repository: ScreenLockRepository) {
    operator fun invoke(pin: String): Boolean = repository.verifyPassword(pin)
}

/** 패턴이 맞는지. */
class VerifyScreenLockPatternUseCase(private val repository: ScreenLockRepository) {
    operator fun invoke(dots: List<Int>): Boolean = repository.verifyPattern(dots)
}

/** 패턴을 그릴 때 선을 보일지. */
class SetScreenLockPatternVisibleUseCase(private val repository: ScreenLockRepository) {
    operator fun invoke(visible: Boolean) = repository.setPatternVisible(visible)
}

/** 생체 인증으로 풀기를 켜고 끈다. */
class SetScreenLockBiometricUseCase(private val repository: ScreenLockRepository) {
    operator fun invoke(enabled: Boolean) = repository.setBiometricEnabled(enabled)
}

/** 생체 인증을 더 쓸 수 없게 됐을 때 설정을 지운다. */
class ClearScreenLockBiometricUseCase(private val repository: ScreenLockRepository) {
    operator fun invoke() = repository.clearBiometric()
}

/** 기기에 지금 쓸 수 있는 생체 정보가 등록돼 있는지. */
class IsBiometricAvailableUseCase(private val biometric: BiometricAuthenticator) {
    operator fun invoke(): Boolean = biometric.isAvailable()
}

/** 생체 인증 창을 띄우고 결과를 돌려준다. */
class AuthenticateBiometricUseCase(private val biometric: BiometricAuthenticator) {
    suspend operator fun invoke(title: String, subtitle: String, negativeText: String): BiometricResult =
        biometric.authenticate(title, subtitle, negativeText)
}

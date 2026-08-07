package com.eunilsung.talk.ui.screenlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.eunilsung.talk.data.local.BiometricAuthenticator
import com.eunilsung.talk.data.local.BiometricResult
import com.eunilsung.talk.domain.model.ScreenLockConfig
import com.eunilsung.talk.domain.repository.ScreenLockRepository

/** 화면 잠금 설정/검증 ViewModel — 설정 화면과 잠금 화면에서 공용. */
class ScreenLockViewModel(
    private val repository: ScreenLockRepository,
    private val biometric: BiometricAuthenticator,
) : ViewModel() {

    val config: StateFlow<ScreenLockConfig> = repository.config

    /** 기기 생체 사용 가능 여부(등록됨). */
    private val _biometricAvailable = MutableStateFlow(biometric.isAvailable())
    val biometricAvailable: StateFlow<Boolean> = _biometricAvailable.asStateFlow()

    /** 생체 가용성 재확인 + 불가한데 켜져 있으면 자동 해제. */
    fun reconcileBiometric() {
        val available = biometric.isAvailable()
        _biometricAvailable.value = available
        if (!available && repository.config.value.biometricEnabled) {
            repository.clearBiometric()
        }
    }

    fun disableLock() {
        repository.disable()
        ScreenLockController.reset()
    }

    fun setPassword(pin: String) = repository.setPassword(pin)
    fun setPattern(dots: List<Int>) = repository.setPattern(dots)
    fun verifyPassword(pin: String): Boolean = repository.verifyPassword(pin)
    fun verifyPattern(dots: List<Int>): Boolean = repository.verifyPattern(dots)
    fun setPatternVisible(visible: Boolean) = repository.setPatternVisible(visible)

    /** 생체 토글 — 켤 때는 실제 사용 가능한 경우에만 적용. */
    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && !biometric.isAvailable()) {
            reconcileBiometric()
            return
        }
        repository.setBiometricEnabled(enabled)
    }

    /** 생체 프롬프트. 결과를 콜백으로 전달. */
    fun authenticateBiometric(
        title: String,
        subtitle: String,
        negativeText: String,
        onResult: (BiometricResult) -> Unit,
    ) {
        viewModelScope.launch {
            val result = biometric.authenticate(title, subtitle, negativeText)
            if (result == BiometricResult.UNAVAILABLE &&
                repository.config.value.biometricEnabled &&
                !biometric.isAvailable()
            ) {
                repository.clearBiometric()
                _biometricAvailable.value = false
            }
            onResult(result)
        }
    }
}

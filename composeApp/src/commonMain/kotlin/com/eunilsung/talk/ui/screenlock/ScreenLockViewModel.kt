package com.eunilsung.talk.ui.screenlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eunilsung.talk.Platform
import com.eunilsung.talk.domain.repository.BiometricResult
import com.eunilsung.talk.domain.usecase.ScreenLockUseCases

/** 화면 잠금 설정/검증 ViewModel — 설정 화면과 잠금 화면에서 공용. */
class ScreenLockViewModel(
    private val useCases: ScreenLockUseCases,
    platform: Platform,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ScreenLockUiState(
            config = useCases.observeConfig().value,
            biometricAvailable = useCases.isBiometricAvailable(),
            isAndroid = platform.name == "ANDROID",
        )
    )
    val uiState: StateFlow<ScreenLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            useCases.observeConfig().collect { config -> _uiState.update { it.copy(config = config) } }
        }
    }

    fun onAction(action: ScreenLockActions) {
        when (action) {
            is ScreenLockActions.ReconcileBiometric -> reconcileBiometric()
            is ScreenLockActions.DisableLock -> {
                useCases.disable()
                ScreenLockController.reset()
            }
            is ScreenLockActions.SetPassword -> useCases.setPassword(action.pin)
            is ScreenLockActions.SetPattern -> useCases.setPattern(action.dots)
            is ScreenLockActions.SetPatternVisible -> useCases.setPatternVisible(action.visible)
            is ScreenLockActions.SetBiometricEnabled -> setBiometricEnabled(action.enabled)
            is ScreenLockActions.UnlockWithPassword -> unlockIf(useCases.verifyPassword(action.pin))
            is ScreenLockActions.UnlockWithPattern -> unlockIf(useCases.verifyPattern(action.dots))
            is ScreenLockActions.UnlockWithBiometric -> authenticateBiometric(action)
        }
    }

    /** 생체 가용성 재확인 + 불가한데 켜져 있으면 자동 해제. */
    private fun reconcileBiometric() {
        val available = useCases.isBiometricAvailable()
        _uiState.update { it.copy(biometricAvailable = available) }
        if (!available && useCases.observeConfig().value.biometricEnabled) {
            useCases.clearBiometric()
        }
    }

    private fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && !useCases.isBiometricAvailable()) {
            reconcileBiometric()
            return
        }
        useCases.setBiometricEnabled(enabled)
    }

    private fun unlockIf(verified: Boolean) {
        if (verified) unlock() else _uiState.update { it.copy(unlockFailCount = it.unlockFailCount + 1) }
    }

    private fun unlock() {
        _uiState.update { it.copy(unlockFailCount = 0) }
        ScreenLockController.unlock()
    }

    private fun authenticateBiometric(action: ScreenLockActions.UnlockWithBiometric) {
        viewModelScope.launch {
            val result = useCases.authenticateBiometric(action.title, action.subtitle, action.negativeText)
            if (result == BiometricResult.UNAVAILABLE &&
                useCases.observeConfig().value.biometricEnabled &&
                !useCases.isBiometricAvailable()
            ) {
                useCases.clearBiometric()
                _uiState.update { it.copy(biometricAvailable = false) }
            }
            if (result == BiometricResult.SUCCESS) unlock()
        }
    }
}

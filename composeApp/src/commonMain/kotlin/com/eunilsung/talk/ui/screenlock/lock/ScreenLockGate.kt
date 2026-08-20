package com.eunilsung.talk.ui.screenlock.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.eunilsung.talk.Platform
import com.eunilsung.talk.data.local.BiometricResult
import com.eunilsung.talk.domain.model.LockType
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.domain.repository.ScreenLockRepository
import com.eunilsung.talk.ui.screenlock.ScreenLockController
import com.eunilsung.talk.ui.screenlock.ScreenLockViewModel
import com.eunilsung.talk.ui.screenlock.component.PatternPad
import com.eunilsung.talk.ui.screenlock.component.PinPad
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.pointer.consumeAllPointerEvents
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.lock_biometric_negative_password
import multiplatformtalk.composeapp.generated.resources.lock_biometric_prompt_sub_faceid
import multiplatformtalk.composeapp.generated.resources.lock_biometric_prompt_sub_fingerprint
import multiplatformtalk.composeapp.generated.resources.lock_biometric_prompt_title
import multiplatformtalk.composeapp.generated.resources.lock_unlock_mismatch
import multiplatformtalk.composeapp.generated.resources.lock_unlock_password_hint
import multiplatformtalk.composeapp.generated.resources.lock_unlock_pattern_hint
import multiplatformtalk.composeapp.generated.resources.lock_unlock_title_password
import multiplatformtalk.composeapp.generated.resources.lock_unlock_title_pattern
import multiplatformtalk.composeapp.generated.resources.lock_unlock_with_faceid
import multiplatformtalk.composeapp.generated.resources.lock_unlock_with_fingerprint
import com.eunilsung.talk.ui.uikit.BackHandler
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** 앱 전역 화면 잠금 게이트 — 잠금 사용 중 + 로그인 + locked 이면 전체화면 잠금 표시. */
@Composable
fun ScreenLockGate() {
    val repo: ScreenLockRepository = koinInject()
    val loginRepository: LoginRepository = koinInject()
    val config by repo.config.collectAsState()
    val locked by ScreenLockController.locked.collectAsState()
    val isLoggedIn by loginRepository.isLoggedIn.collectAsState()

    LaunchedEffect(config.isEnabled, isLoggedIn) {
        if (isLoggedIn) ScreenLockController.ensureInitialLock(config.isEnabled)
        else ScreenLockController.reset()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        ScreenLockController.onBackground(config.isEnabled && isLoggedIn)
    }

    if (config.isEnabled && isLoggedIn && locked) {
        LockScreenHost(onUnlocked = { ScreenLockController.unlock() })
    }
}

@Composable
private fun LockScreenHost(onUnlocked: () -> Unit) {
    val vm: ScreenLockViewModel = koinViewModel()
    val config by vm.config.collectAsState()
    val biometricAvailable by vm.biometricAvailable.collectAsState()
    val platform: Platform = koinInject()
    val isAndroid = platform.name == "ANDROID"

    var errorKey by remember { mutableStateOf(0) }
    var isError by remember { mutableStateOf(false) }

    val moveToBackground = com.eunilsung.talk.ui.uikit.rememberMoveToBackground()
    BackHandler(enabled = true) { moveToBackground() }

    val bioTitle = stringResource(Res.string.lock_biometric_prompt_title)
    val bioSubtitle = stringResource(
        if (isAndroid) Res.string.lock_biometric_prompt_sub_fingerprint
        else Res.string.lock_biometric_prompt_sub_faceid
    )
    val bioNegative = stringResource(Res.string.cancel)

    fun runBiometric() {
        vm.authenticateBiometric(
            title = bioTitle,
            subtitle = bioSubtitle,
            negativeText = bioNegative,
        ) { result ->
            if (result == BiometricResult.SUCCESS) onUnlocked()
        }
    }

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var promptFired by remember { mutableStateOf(false) }

    fun tryAutoPromptBiometric() {
        if (promptFired) return
        if (vm.config.value.biometricEnabled && vm.biometricAvailable.value) {
            promptFired = true
            runBiometric()
        }
    }

    LaunchedEffect(Unit) {
        vm.reconcileBiometric()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tryAutoPromptBiometric()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tryAutoPromptBiometric() }

    /**
     * 백그라운드로 나가면 자동 프롬프트를 다시 무장한다.
     *
     * [promptFired] 는 **한 번의 포그라운드 세션에서** 프롬프트가 중복으로 뜨는 것을 막으려는 것이지
     * 앱 수명 전체에서 1회로 제한하려는 게 아니다. 리셋하지 않으면 사용자가 지문 프롬프트를 취소한
     * 뒤에는 잠금 화면이 그대로 남아([LockScreenHost] 가 컴포지션에 유지되므로) 플래그도 살아 있어,
     * 다시 포그라운드로 들어와도 지문이 뜨지 않는다.
     *
     * [Lifecycle.Event.ON_PAUSE] 가 아니라 ON_STOP 인 이유 — 생체 프롬프트가 뜰 때 ON_PAUSE 가
     * 발생하므로, 거기서 리셋하면 프롬프트를 띄우자마자 무장이 풀려 중복으로 뜰 수 있다.
     */
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { promptFired = false }

    LockScreenContent(
        type = config.type,
        patternVisible = config.patternVisible,
        isError = isError,
        errorKey = errorKey,
        biometricVisible = config.biometricEnabled && biometricAvailable,
        isAndroid = isAndroid,
        onPinEntered = { pin ->
            if (vm.verifyPassword(pin)) onUnlocked() else { isError = true; errorKey++ }
        },
        onPatternEntered = { dots ->
            if (vm.verifyPattern(dots)) onUnlocked() else { isError = true; errorKey++ }
        },
        onBiometricClick = { runBiometric() },
    )
}

/** 잠금 해제 화면 시각 — 상태 없는 순수 UI (Preview 가능). */
@Composable
private fun LockScreenContent(
    type: LockType,
    patternVisible: Boolean,
    isError: Boolean,
    errorKey: Int,
    biometricVisible: Boolean,
    isAndroid: Boolean,
    onPinEntered: (String) -> Unit,
    onPatternEntered: (List<Int>) -> Unit,
    onBiometricClick: () -> Unit,
) {
    Box(
        // 잠금 화면은 앱 본문 위에 겹쳐 그려져 배경색만으로는 시각적으로만 가린다.
        // 이벤트까지 흡수해야 잠긴 상태에서 뒤쪽 대화방 등이 조작되지 않는다.
        modifier = Modifier.fillMaxSize().background(AppColors.Bg).consumeAllPointerEvents(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val defaultSub = stringResource(
                if (type == LockType.PATTERN) Res.string.lock_unlock_pattern_hint
                else Res.string.lock_unlock_password_hint
            )
            val subtitle = if (isError) stringResource(Res.string.lock_unlock_mismatch) else defaultSub
            val subColor = if (isError) AppColors.Red else AppColors.TextSub

            when (type) {
                LockType.PASSWORD -> PinPad(
                    title = stringResource(Res.string.lock_unlock_title_password),
                    subtitle = subtitle,
                    subtitleColor = subColor,
                    errorKey = errorKey,
                    onEntered = onPinEntered,
                )
                LockType.PATTERN -> PatternPad(
                    title = stringResource(Res.string.lock_unlock_title_pattern),
                    subtitle = subtitle,
                    subtitleColor = subColor,
                    visibleTrail = patternVisible,
                    errorKey = errorKey,
                    onEntered = onPatternEntered,
                )
                LockType.NONE -> Unit
            }

            if (biometricVisible) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(
                        if (isAndroid) Res.string.lock_unlock_with_fingerprint
                        else Res.string.lock_unlock_with_faceid
                    ),
                    color = AppColors.Main,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { onBiometricClick() }.padding(12.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun LockScreenContentPasswordPreview() {
    MaterialTheme {
        LockScreenContent(
            type = LockType.PASSWORD,
            patternVisible = true,
            isError = false,
            errorKey = 0,
            biometricVisible = true,
            isAndroid = true,
            onPinEntered = {},
            onPatternEntered = {},
            onBiometricClick = {},
        )
    }
}

@Preview
@Composable
private fun LockScreenContentPatternPreview() {
    MaterialTheme {
        LockScreenContent(
            type = LockType.PATTERN,
            patternVisible = true,
            isError = true,
            errorKey = 0,
            biometricVisible = false,
            isAndroid = true,
            onPinEntered = {},
            onPatternEntered = {},
            onBiometricClick = {},
        )
    }
}

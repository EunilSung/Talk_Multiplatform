package com.eunilsung.talk.ui.screenlock.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eunilsung.talk.Platform
import com.eunilsung.talk.domain.model.LockType
import com.eunilsung.talk.domain.model.ScreenLockConfig
import com.eunilsung.talk.ui.screenlock.ScreenLockViewModel
import com.eunilsung.talk.ui.screenlock.component.PatternPad
import com.eunilsung.talk.ui.screenlock.component.PinPad
import com.eunilsung.talk.ui.setting.component.SettingItemRadio
import com.eunilsung.talk.ui.setting.component.SettingItemText
import com.eunilsung.talk.ui.setting.component.SettingItemToggle
import com.eunilsung.talk.ui.setting.detail.SettingDetailScaffold
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.line.LineDivider
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.lock_biometric_faceid
import multiplatformtalk.composeapp.generated.resources.lock_biometric_fingerprint
import multiplatformtalk.composeapp.generated.resources.lock_change_password
import multiplatformtalk.composeapp.generated.resources.lock_change_pattern
import multiplatformtalk.composeapp.generated.resources.lock_pattern_too_short
import multiplatformtalk.composeapp.generated.resources.lock_screen_title
import multiplatformtalk.composeapp.generated.resources.lock_setup_password_confirm
import multiplatformtalk.composeapp.generated.resources.lock_setup_password_mismatch
import multiplatformtalk.composeapp.generated.resources.lock_setup_password_new
import multiplatformtalk.composeapp.generated.resources.lock_setup_password_title
import multiplatformtalk.composeapp.generated.resources.lock_setup_pattern_confirm
import multiplatformtalk.composeapp.generated.resources.lock_setup_pattern_mismatch
import multiplatformtalk.composeapp.generated.resources.lock_setup_pattern_new
import multiplatformtalk.composeapp.generated.resources.lock_setup_pattern_title
import multiplatformtalk.composeapp.generated.resources.lock_show_pattern
import multiplatformtalk.composeapp.generated.resources.lock_type_none
import multiplatformtalk.composeapp.generated.resources.lock_type_password
import multiplatformtalk.composeapp.generated.resources.lock_type_pattern
import multiplatformtalk.composeapp.generated.resources.lock_warning
import com.eunilsung.talk.ui.setting.component.SettingItemNavigate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val MIN_PATTERN = 4

/** 화면 잠금 설정 — 모드(사용 안 함/비밀번호/패턴) 라디오 + 모드별 하위항목 + 경고문구. */
class ScreenLockSettingScreen : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val vm: ScreenLockViewModel = koinViewModel()
        val config by vm.config.collectAsState()
        val biometricAvailable by vm.biometricAvailable.collectAsState()
        val platform: Platform = koinInject()

        LaunchedEffect(Unit) { vm.reconcileBiometric() }

        var setupType by remember { mutableStateOf<LockType?>(null) }

        SettingDetailScaffold(title = stringResource(Res.string.lock_screen_title)) { padding ->
            val type = setupType
            if (type != null) {
                LockSetup(
                    type = type,
                    patternVisible = config.patternVisible,
                    padding = padding,
                    onPasswordSet = { vm.setPassword(it); setupType = null },
                    onPatternSet = { vm.setPattern(it); setupType = null },
                    onCancel = { setupType = null },
                )
            } else {
                ScreenLockSettingBody(
                    config = config,
                    biometricAvailable = biometricAvailable,
                    isAndroid = platform.name == "ANDROID",
                    padding = padding,
                    onSelectNone = { if (config.type != LockType.NONE) vm.disableLock() },
                    onSelectPassword = { if (config.type != LockType.PASSWORD) setupType = LockType.PASSWORD },
                    onSelectPattern = { if (config.type != LockType.PATTERN) setupType = LockType.PATTERN },
                    onToggleBiometric = { vm.setBiometricEnabled(it) },
                    onTogglePatternVisible = { vm.setPatternVisible(it) },
                    onChange = { setupType = config.type },
                )
            }
        }
    }
}

@Composable
private fun ScreenLockSettingBody(
    config: ScreenLockConfig,
    biometricAvailable: Boolean,
    isAndroid: Boolean,
    padding: PaddingValues,
    onSelectNone: () -> Unit,
    onSelectPassword: () -> Unit,
    onSelectPattern: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onTogglePatternVisible: (Boolean) -> Unit,
    onChange: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
    ) {
        SettingItemRadio(stringResource(Res.string.lock_type_none), config.type == LockType.NONE, onSelectNone)
        LineDivider(Modifier.fillMaxWidth().height(1.dp))
        SettingItemRadio(stringResource(Res.string.lock_type_password), config.type == LockType.PASSWORD, onSelectPassword)
        LineDivider(Modifier.fillMaxWidth().height(1.dp))
        SettingItemRadio(stringResource(Res.string.lock_type_pattern), config.type == LockType.PATTERN, onSelectPattern)
        LineDivider(Modifier.fillMaxWidth().height(1.dp))

        if (config.type != LockType.NONE) {
            if (biometricAvailable) {
                SettingItemToggle(
                    title = stringResource(
                        if (isAndroid) Res.string.lock_biometric_fingerprint else Res.string.lock_biometric_faceid
                    ),
                    checked = config.biometricEnabled,
                    onCheckedChange = onToggleBiometric,
                )
                LineDivider(Modifier.fillMaxWidth().height(1.dp))
            }

            SettingItemNavigate(
                title = stringResource(
                    if (config.type == LockType.PASSWORD) Res.string.lock_change_password else Res.string.lock_change_pattern
                ),
                valueText = "",
                onClick = onChange
            )

            if (config.type == LockType.PATTERN) {
                LineDivider(Modifier.fillMaxWidth().height(1.dp))
                SettingItemToggle(stringResource(Res.string.lock_show_pattern), config.patternVisible, onTogglePatternVisible)
            }
            LineDivider(Modifier.fillMaxWidth().height(1.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "⚠ " + stringResource(Res.string.lock_warning),
            color = AppColors.Red,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

/** 비밀번호/패턴 설정 — 입력 → 재입력 확인. 불일치 시 처음부터 재입력. */
@Composable
private fun LockSetup(
    type: LockType,
    patternVisible: Boolean,
    padding: PaddingValues,
    onPasswordSet: (String) -> Unit,
    onPatternSet: (List<Int>) -> Unit,
    onCancel: () -> Unit,
) {
   
    val txtPasswordTitle = stringResource(Res.string.lock_setup_password_title)
    val txtPatternTitle = stringResource(Res.string.lock_setup_pattern_title)
    val txtPasswordNew = stringResource(Res.string.lock_setup_password_new)
    val txtPatternNew = stringResource(Res.string.lock_setup_pattern_new)
    val txtPasswordConfirm = stringResource(Res.string.lock_setup_password_confirm)
    val txtPasswordMismatch = stringResource(Res.string.lock_setup_password_mismatch)
    val txtPatternConfirm = stringResource(Res.string.lock_setup_pattern_confirm)
    val txtPatternMismatch = stringResource(Res.string.lock_setup_pattern_mismatch)
    val txtTooShort = stringResource(Res.string.lock_pattern_too_short, MIN_PATTERN)
    val txtCancel = stringResource(Res.string.cancel)

    var firstPin by remember { mutableStateOf<String?>(null) }
    var firstDots by remember { mutableStateOf<List<Int>?>(null) }
    var subtitle by remember {
        mutableStateOf(if (type == LockType.PASSWORD) txtPasswordNew else txtPatternNew)
    }
    var errorKey by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        if (type == LockType.PASSWORD) {
            PinPad(
                title = txtPasswordTitle,
                subtitle = subtitle,
                errorKey = errorKey,
                onEntered = { pin ->
                    val f = firstPin
                    if (f == null) {
                        firstPin = pin
                        subtitle = txtPasswordConfirm
                        errorKey++
                    } else if (pin == f) {
                        onPasswordSet(pin)
                    } else {
                        firstPin = null
                        subtitle = txtPasswordMismatch
                        errorKey++
                    }
                },
            )
        } else {
            PatternPad(
                title = txtPatternTitle,
                subtitle = subtitle,
                visibleTrail = patternVisible,
                errorKey = errorKey,
                onEntered = { dots ->
                    if (dots.size < MIN_PATTERN) {
                        subtitle = txtTooShort
                        errorKey++
                        return@PatternPad
                    }
                    val f = firstDots
                    if (f == null) {
                        firstDots = dots
                        subtitle = txtPatternConfirm
                        errorKey++
                    } else if (dots == f) {
                        onPatternSet(dots)
                    } else {
                        firstDots = null
                        subtitle = txtPatternMismatch
                        errorKey++
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text(txtCancel, color = AppColors.TextSub) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScreenLockSettingBodyPatternPreview() {
    MaterialTheme {
        ScreenLockSettingBody(
            config = ScreenLockConfig(type = LockType.PATTERN, biometricEnabled = true, patternVisible = true),
            biometricAvailable = true,
            isAndroid = true,
            padding = PaddingValues(0.dp),
            onSelectNone = {}, onSelectPassword = {}, onSelectPattern = {},
            onToggleBiometric = {}, onTogglePatternVisible = {}, onChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScreenLockSettingBodyNonePreview() {
    MaterialTheme {
        ScreenLockSettingBody(
            config = ScreenLockConfig(type = LockType.NONE),
            biometricAvailable = false,
            isAndroid = true,
            padding = PaddingValues(0.dp),
            onSelectNone = {}, onSelectPassword = {}, onSelectPattern = {},
            onToggleBiometric = {}, onTogglePatternVisible = {}, onChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LockSetupPasswordPreview() {
    MaterialTheme {
        LockSetup(
            type = LockType.PASSWORD,
            patternVisible = true,
            padding = PaddingValues(0.dp),
            onPasswordSet = {}, onPatternSet = {}, onCancel = {},
        )
    }
}

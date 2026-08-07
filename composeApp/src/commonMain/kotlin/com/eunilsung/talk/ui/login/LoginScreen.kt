package com.eunilsung.talk.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.id
import multiplatformtalk.composeapp.generated.resources.login
import multiplatformtalk.composeapp.generated.resources.logo
import multiplatformtalk.composeapp.generated.resources.pw
import multiplatformtalk.composeapp.generated.resources.save_pw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import com.eunilsung.talk.data.local.AppExiter
import com.eunilsung.talk.data.local.ExternalUrlOpener
import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.domain.model.VersionInfo
import com.eunilsung.talk.domain.usecase.VersionCheckUseCase
import com.eunilsung.talk.ui.main.DownloadingDialog
import com.eunilsung.talk.ui.main.ForceUpdateOverlay
import com.eunilsung.talk.ui.main.KeyBoardPane
import com.eunilsung.talk.ui.main.rememberImeHeight
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.util.Log
import org.koin.compose.koinInject
import com.eunilsung.talk.ui.uikit.button.ButtonV1
import com.eunilsung.talk.ui.uikit.checkbox.ToggleV1
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import com.eunilsung.talk.ui.uikit.textfield.TextFieldV1
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val inputState by viewModel.inputState.collectAsState()
    var showInputs by remember { mutableStateOf(false) }
    val versionCheckUseCase: VersionCheckUseCase = koinInject()
    val externalUrlOpener: ExternalUrlOpener = koinInject()
    val appExiter: AppExiter = koinInject()
    var versionInfo by remember { mutableStateOf<VersionInfo?>(null) }
    var updateRequested by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (updateRequested) {
            Log.message("[VersionCheck] update flow → background → exit")
            appExiter.exit()
        }
    }

    LaunchedEffect(Unit) {
        versionInfo = runCatching { versionCheckUseCase() }.getOrNull()

        if (versionInfo?.needsUpdate == true) return@LaunchedEffect

        val isAutoLoginAvailable = inputState.id.isNotEmpty() && inputState.pw.isNotEmpty()
        if (isAutoLoginAvailable) {
            viewModel.onAction(LoginActions.OnLoginClick(inputState))
        } else {
            delay(1000)
            viewModel.onAction(LoginActions.OnResetState)
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is LoginUiState.Loading -> {
                showInputs = false
            }
            is LoginUiState.Idle,
            is LoginUiState.Error -> {
                showInputs = true
            }
            else -> {}
        }
    }

    LoginContent(
        uiState = uiState,
        inputState = inputState,
        onAction = viewModel::onAction,
        showInputs = showInputs
    )

    val isDownloading by externalUrlOpener.isDownloading.collectAsState()
    versionInfo?.takeIf { it.needsUpdate }?.let { info ->
        when {
            isDownloading -> DownloadingDialog()
            else -> ForceUpdateOverlay(
                updateUrl = info.updateUrl,
                onUpdate = {
                    updateRequested = true
                    externalUrlOpener.open(info.updateUrl)
                },
                onCancel = { appExiter.exit() }
            )
        }
    }
}

@Composable
fun LoginContent(
    uiState: LoginUiState,
    inputState: Login.LoginRequest,
    onAction: (LoginActions) -> Unit,
    showInputs: Boolean
){
    val focusManager = LocalFocusManager.current
    val dialog = LocalDialogManager.current
    val imeHeight = rememberImeHeight()
    val isKeyboardVisible = imeHeight > 0.dp
    val loginTitle = stringResource(Res.string.login)

    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.maxValue) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is LoginUiState.Error -> dialog.alert(
                message = uiState.message,
                onConfirm = { onAction(LoginActions.OnResetState) }
            )
            is LoginUiState.Duplicate -> dialog.confirm(
                title = loginTitle,
                message = uiState.message,
                onConfirm = { onAction(LoginActions.OnDuplicateLoginConfirm(inputState)) },
                onCancel = { onAction(LoginActions.OnResetState) }
            )
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

            Box(modifier = Modifier
                .size(150.dp)
                .background(shape = RoundedCornerShape(24.dp), color = AppColors.White)
            ){
                Image(
                    painter = painterResource(Res.drawable.logo),
                    contentDescription = "login logo",
                    modifier = Modifier.size(120.dp).align(Alignment.Center)
                )
            }

            AnimatedVisibility(
                visible = showInputs && uiState !is LoginUiState.Success,
                enter = fadeIn(animationSpec = tween(1000)) +
                        slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(1000)) +
                        expandVertically(animationSpec = tween(1000)),
                exit = fadeOut(animationSpec = tween(1000)) +
                        slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(1000)) +
                        shrinkVertically(animationSpec = tween(1000))
            ) {
                LoginInputForm(inputState = inputState, onAction = onAction)
            }
        }
        }

        KeyBoardPane(
            isKeyboardVisible = isKeyboardVisible,
            imeHeight = imeHeight + 50.dp
        )
    }
}

@Composable
private fun LoginInputForm(
    inputState: Login.LoginRequest,
    onAction: (LoginActions) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier.widthIn(max = 375.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = stringResource(Res.string.id),
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.Text,
            fontSize = 13.sp,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(5.dp))

        TextFieldV1(
            inputText = inputState.id,
            hintText = stringResource(Res.string.id),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true,
            onValueChange = { onAction(LoginActions.OnIdChange(it)) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(Res.string.pw),
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.Text,
            fontSize = 13.sp,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(5.dp))

        TextFieldV1(
            inputText = inputState.pw,
            hintText = stringResource(Res.string.pw),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    onAction(LoginActions.OnLoginClick(inputState))
                }
            ),
            singleLine = true,
            onValueChange = { onAction(LoginActions.OnPwChange(it)) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        ToggleV1(
            modifier = Modifier.align(Alignment.Start),
            text = stringResource(Res.string.save_pw),
            checked = inputState.isSavePw,
            onClick = { onAction(LoginActions.OnToggleSavePw(!inputState.isSavePw)) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        ButtonV1(
            modifier = Modifier.fillMaxWidth().height(50.dp),
            text = stringResource(Res.string.login),
            onClick = {
                keyboardController?.hide()
                onAction(LoginActions.OnLoginClick(inputState))
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    MaterialTheme {
        LoginContent(
            uiState = LoginUiState.Idle,
            inputState = Login.LoginRequest(id = "test1", pw = "1234", isSavePw = true),
            onAction = {},
            showInputs = true
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginLoadingPreview() {
    MaterialTheme {
        LoginContent(
            uiState = LoginUiState.Loading,
            inputState = Login.LoginRequest(),
            onAction = {},
            showInputs = false
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginInputFormPreview() {
    MaterialTheme {
        LoginInputForm(
            inputState = Login.LoginRequest(id = "test1", isSavePw = true),
            onAction = {},
        )
    }
}


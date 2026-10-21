package com.eunilsung.talk.ui.login

import org.jetbrains.compose.resources.getString
import multiplatformtalk.composeapp.generated.resources.login_fail
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
import multiplatformtalk.composeapp.generated.resources.login_demo_hint
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.eunilsung.talk.data.local.AppExiter
import com.eunilsung.talk.data.local.ExternalUrlOpener
import com.eunilsung.talk.domain.model.Login
import com.eunilsung.talk.ui.main.DownloadingDialog
import com.eunilsung.talk.ui.main.ForceUpdateOverlay
import com.eunilsung.talk.ui.main.KeyBoardPane
import com.eunilsung.talk.ui.main.rememberImeHeight
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.util.Log
import org.koin.compose.koinInject
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.platform.testTag
import com.eunilsung.talk.ui.uikit.TestTags
import multiplatformtalk.composeapp.generated.resources.visibility_off_icon
import multiplatformtalk.composeapp.generated.resources.visibility_icon
import multiplatformtalk.composeapp.generated.resources.login_hide_password
import multiplatformtalk.composeapp.generated.resources.login_show_password
import multiplatformtalk.composeapp.generated.resources.login_tagline
import multiplatformtalk.composeapp.generated.resources.app_name
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showInputs by remember { mutableStateOf(false) }
    val versionInfo = uiState.versionInfo
    val externalUrlOpener: ExternalUrlOpener = koinInject()
    val appExiter: AppExiter = koinInject()
    var updateRequested by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        if (updateRequested) {
            Log.message("[VersionCheck] update flow → background → exit")
            appExiter.exit()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onAction(LoginActions.OnStart)
    }

    LaunchedEffect(uiState.status) {
        when (uiState.status) {
            is LoginStatus.Loading -> {
                showInputs = false
            }
            is LoginStatus.Idle,
            is LoginStatus.Error -> {
                showInputs = true
            }
            else -> {}
        }
    }

    LoginContent(
        uiState = uiState,
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
    onAction: (LoginActions) -> Unit,
    showInputs: Boolean
){
    val status = uiState.status
    val inputState = uiState.input
    val focusManager = LocalFocusManager.current
    val dialog = LocalDialogManager.current
    val imeHeight = rememberImeHeight()
    val isKeyboardVisible = imeHeight > 0.dp
    val loginTitle = stringResource(Res.string.login)

    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.maxValue) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    LaunchedEffect(status) {
        when (status) {
            is LoginStatus.Error -> dialog.alert(
                message = status.message.ifBlank { getString(Res.string.login_fail) },
                onConfirm = { onAction(LoginActions.OnResetState) }
            )
            is LoginStatus.Duplicate -> dialog.confirm(
                title = loginTitle,
                message = status.message,
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

            Brand()

            AnimatedVisibility(
                visible = showInputs && status !is LoginStatus.Success,
                enter = fadeIn(animationSpec = tween(1000)) +
                        slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(1000)) +
                        expandVertically(animationSpec = tween(1000)),
                exit = fadeOut(animationSpec = tween(1000)) +
                        slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(1000)) +
                        shrinkVertically(animationSpec = tween(1000))
            ) {
                LoginInputForm(uiState = uiState, onAction = onAction)
            }
        }
        }

        KeyBoardPane(
            isKeyboardVisible = isKeyboardVisible,
            imeHeight = imeHeight + 50.dp
        )
    }
}

/** 브랜드 — 앱 아이콘, 앱 이름, 한 줄 소개. */
@Composable
private fun Brand() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = null,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
        )
        Text(
            text = stringResource(Res.string.app_name),
            color = AppColors.Text,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.login_tagline),
            color = AppColors.TextSub,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun LoginInputForm(
    uiState: LoginUiState,
    onAction: (LoginActions) -> Unit,
) {
    val inputState = uiState.input
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submit = {
        keyboardController?.hide()
        onAction(LoginActions.OnLoginClick(inputState))
    }
    Column(
        modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoginField(
            value = inputState.id,
            onValueChange = { onAction(LoginActions.OnIdChange(it)) },
            label = stringResource(Res.string.id),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = TestTags.LOGIN_ID,
        )
        LoginField(
            value = inputState.pw,
            onValueChange = { onAction(LoginActions.OnPwChange(it)) },
            label = stringResource(Res.string.pw),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (uiState.canSubmit) submit() }),
            testTag = TestTags.LOGIN_PW,
            visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Icon(
                    painter = painterResource(
                        if (uiState.isPasswordVisible) Res.drawable.visibility_off_icon else Res.drawable.visibility_icon
                    ),
                    contentDescription = stringResource(
                        if (uiState.isPasswordVisible) Res.string.login_hide_password else Res.string.login_show_password
                    ),
                    tint = AppColors.TextSub,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onAction(LoginActions.OnTogglePasswordVisible) }
                        .padding(9.dp),
                )
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onAction(LoginActions.OnToggleSavePw(!inputState.isSavePw)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = inputState.isSavePw,
                onCheckedChange = { onAction(LoginActions.OnToggleSavePw(it)) },
                colors = CheckboxDefaults.colors(checkedColor = AppColors.PrimaryMain),
            )
            Text(text = stringResource(Res.string.save_pw), color = AppColors.Text, fontSize = 14.sp)
        }

        LoginButton(enabled = uiState.canSubmit, onClick = submit)

        /** 회원가입이 없어 로컬·서버 모드 모두 이 계정으로만 들어온다. */
        Text(
            text = stringResource(Res.string.login_demo_hint),
            color = AppColors.TextSub,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.Gray50Bg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(TestTags.LOGIN_DEMO_HINT),
        )
    }
}

/** 떠 있는 라벨이 붙은 테두리 입력칸. */
@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    testTag: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.PrimaryMain,
            focusedLabelColor = AppColors.PrimaryMain,
            unfocusedBorderColor = AppColors.Line,
            cursorColor = AppColors.PrimaryMain,
            focusedTextColor = AppColors.Text,
            unfocusedTextColor = AppColors.Text,
            unfocusedLabelColor = AppColors.TextSub,
        ),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

/** 로그인 버튼 — 아이디·비밀번호를 다 넣어야 눌린다. */
@Composable
private fun LoginButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .height(52.dp)
            .testTag(TestTags.LOGIN_SUBMIT)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) AppColors.PrimaryMain else AppColors.Gray100BgAuto)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.login),
            color = if (enabled) AppColors.White else AppColors.TextDisabled,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    MaterialTheme {
        LoginContent(
            uiState = LoginUiState(
                status = LoginStatus.Idle,
                input = Login.LoginRequest(id = "test1", pw = "1234", isSavePw = true),
            ),
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
            uiState = LoginUiState(status = LoginStatus.Loading),
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
            uiState = LoginUiState(status = LoginStatus.Idle, input = Login.LoginRequest(id = "test1", isSavePw = true)),
            onAction = {},
        )
    }
}


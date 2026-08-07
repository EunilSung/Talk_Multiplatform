package com.eunilsung.talk.ui.setting.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eunilsung.talk.ui.setting.SettingActions
import com.eunilsung.talk.ui.setting.SettingViewModel
import com.eunilsung.talk.ui.setting.component.SettingItemButton
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import com.eunilsung.talk.ui.uikit.line.LineDivider
import org.koin.compose.viewmodel.koinViewModel
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.setting_security
import multiplatformtalk.composeapp.generated.resources.logout
import multiplatformtalk.composeapp.generated.resources.confirm_logout
import multiplatformtalk.composeapp.generated.resources.logout_mobile_desc
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.lock_screen_title
import multiplatformtalk.composeapp.generated.resources.setting_noti_sound_item
import com.eunilsung.talk.ui.setting.component.SettingItemNavigate
import com.eunilsung.talk.ui.setting.overlay.notificationSoundLabel
import org.jetbrains.compose.resources.stringResource

class SettingSecurityDetailScreen : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: SettingViewModel = koinViewModel()
        val dialog = LocalDialogManager.current
        val txtLogout = stringResource(Res.string.logout)
        val txtConfirmLogout = stringResource(Res.string.confirm_logout)
        val txtCancel = stringResource(Res.string.cancel)

        val navigator = LocalNavigator.currentOrThrow

        SettingDetailScaffold(title = stringResource(Res.string.setting_security)) { padding ->
            SecurityDetailBody(
                padding = padding,
                onScreenLock = {
                    navigator.push(com.eunilsung.talk.ui.screenlock.setting.ScreenLockSettingScreen())
                },
                onMobileLogout = {
                    dialog.confirm(
                        title = txtLogout,
                        message = txtConfirmLogout,
                        confirmText = txtLogout,
                        dismissText = txtCancel,
                        onConfirm = { viewModel.onAction(SettingActions.Logout) }
                    )
                },
            )
        }
    }
}

@Composable
private fun SecurityDetailBody(
    padding: PaddingValues,
    onScreenLock: () -> Unit,
    onMobileLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        SettingItemButton(
            title = stringResource(Res.string.logout_mobile_desc),
            buttonText = stringResource(Res.string.logout),
            onClick = onMobileLogout,
            destructive = false
        )
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        SettingItemNavigate(
            title = stringResource(Res.string.lock_screen_title),
            valueText = "",
            onClick = onScreenLock
        )
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SecurityDetailBodyPreview() {
    MaterialTheme {
        SecurityDetailBody(padding = PaddingValues(0.dp), onScreenLock = {}, onMobileLogout = {})
    }
}

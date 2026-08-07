package com.eunilsung.talk.ui.setting.detail

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.eunilsung.talk.domain.repository.NotificationSettingsState
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.setting.SettingActions
import com.eunilsung.talk.ui.setting.SettingViewModel
import com.eunilsung.talk.ui.setting.component.SettingItemNavigate
import com.eunilsung.talk.ui.setting.component.SettingItemToggle
import com.eunilsung.talk.ui.setting.component.SettingItemToggleWithDescription
import com.eunilsung.talk.ui.setting.overlay.SettingNotificationSoundScreen
import com.eunilsung.talk.ui.setting.overlay.notificationSoundLabel
import com.eunilsung.talk.ui.uikit.sheet.LocalBottomSheetManager
import com.eunilsung.talk.ui.uikit.line.LineDivider
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import org.koin.compose.viewmodel.koinViewModel
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.setting_notification
import multiplatformtalk.composeapp.generated.resources.setting_noti_message
import multiplatformtalk.composeapp.generated.resources.setting_noti_pc_login_only
import multiplatformtalk.composeapp.generated.resources.setting_noti_preview
import multiplatformtalk.composeapp.generated.resources.setting_noti_preview_desc
import multiplatformtalk.composeapp.generated.resources.setting_noti_sound
import multiplatformtalk.composeapp.generated.resources.setting_noti_vibrate
import multiplatformtalk.composeapp.generated.resources.setting_noti_sound_item
import multiplatformtalk.composeapp.generated.resources.setting_noti_time_setting
import multiplatformtalk.composeapp.generated.resources.setting_noti_time_setting_desc
import org.jetbrains.compose.resources.stringResource

class SettingNotificationDetailScreen : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: SettingViewModel = koinViewModel()
        val noti by viewModel.notificationSettings.collectAsState()
        val showOverlay = LocalFullScreenOverlay.current
        val toastManager = LocalToastManager.current

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.onAction(SettingActions.SyncSystemNotificationState)
        }

        LaunchedEffect(viewModel) {
            viewModel.toastEvents.collect { toastManager.show(it) }
        }

        SettingDetailScaffold(title = stringResource(Res.string.setting_notification)) { padding ->
            NotificationDetailBody(
                padding = padding,
                state = noti,
                isAndroid = viewModel.isAndroid,
                onAction = viewModel::onAction,
                onOpenSound = { showOverlay(SettingNotificationSoundScreen()) }
            )
        }
    }
}

@Composable
private fun NotificationDetailBody(
    padding: PaddingValues,
    state: NotificationSettingsState,
    isAndroid: Boolean,
    onAction: (SettingActions) -> Unit,
    onOpenSound: () -> Unit,
) {
    val bottomSheet = LocalBottomSheetManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        SettingItemToggle(
            title = stringResource(Res.string.setting_noti_message),
            checked = state.enabled,
            onCheckedChange = { onAction(SettingActions.OpenSystemNotificationSettings) },
        )
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        SettingItemToggleWithDescription(
            title = stringResource(Res.string.setting_noti_preview),
            description = stringResource(Res.string.setting_noti_preview_desc),
            checked = state.preview,
            onCheckedChange = { onAction(SettingActions.SetNotificationPreview(it)) },
        )
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        SettingItemToggleWithDescription(
            title = stringResource(Res.string.setting_noti_time_setting),
            description = if (state.timeLimit && state.timeLimitValue.length >= 8) {
                formatNotiTimeRange(state.timeLimitValue)
            } else {
                stringResource(Res.string.setting_noti_time_setting_desc)
            },
            checked = state.timeLimit,
            onCheckedChange = { turnOn ->
                if (turnOn) {
                    bottomSheet.custom {
                        NotiTimePickerSheetContent(
                            initialValue = state.timeLimitValue,
                            onCancel = { bottomSheet.hide() },
                            onConfirm = { value ->
                                bottomSheet.hide()
                                onAction(SettingActions.SetNotificationTimeLimit(enabled = true, value = value))
                            },
                        )
                    }
                } else {
                    onAction(SettingActions.SetNotificationTimeLimit(enabled = false, value = "0"))
                }
            },
        )
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        SettingItemToggle(
            title = stringResource(Res.string.setting_noti_pc_login_only),
            checked = state.pcOff,
            onCheckedChange = { onAction(SettingActions.SetNotificationPcOff(it)) },
        )
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        if (isAndroid) {
            SettingItemToggle(
                title = stringResource(Res.string.setting_noti_sound),
                checked = state.sound,
                onCheckedChange = { onAction(SettingActions.SetNotificationSound(it)) },
            )
        }
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        if (isAndroid) {
            SettingItemToggle(
                title = stringResource(Res.string.setting_noti_vibrate),
                checked = state.vibrate,
                onCheckedChange = { onAction(SettingActions.SetNotificationVibrate(it)) },
            )
            LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        }
        SettingItemNavigate(
            title = stringResource(Res.string.setting_noti_sound_item),
            valueText = notificationSoundLabel(state.soundId),
            onClick = onOpenSound
        )
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
    }
}

/** 알림 시간 값 "시작hhmm+종료hhmm"(예 "09001800") → "09:00 ~ 18:00". */
private fun formatNotiTimeRange(value: String): String {
    fun hhmm(v: String) = "${v.take(2)}:${v.drop(2).take(2)}"
    return "${hhmm(value.take(4))} ~ ${hhmm(value.drop(4).take(4))}"
}

@Preview(showBackground = true)
@Composable
private fun NotificationDetailBodyPreview() {
    MaterialTheme {
        NotificationDetailBody(
            padding = PaddingValues(0.dp),
            state = NotificationSettingsState(),
            isAndroid = true,
            onAction = {},
            onOpenSound = {}
        )
    }
}

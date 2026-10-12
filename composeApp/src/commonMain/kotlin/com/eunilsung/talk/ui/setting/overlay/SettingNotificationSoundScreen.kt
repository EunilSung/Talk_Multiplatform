package com.eunilsung.talk.ui.setting.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.eunilsung.talk.domain.model.NotificationSoundCatalog
import com.eunilsung.talk.ui.setting.component.SettingItemRadio
import com.eunilsung.talk.ui.setting.SettingActions
import com.eunilsung.talk.ui.setting.SettingViewModel
import com.eunilsung.talk.ui.uikit.line.LineDivider
import org.koin.compose.viewmodel.koinViewModel
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.setting_noti_sound_title
import org.jetbrains.compose.resources.stringResource
import multiplatformtalk.composeapp.generated.resources.notification_sound_ding
import multiplatformtalk.composeapp.generated.resources.notification_sound_button_1
import multiplatformtalk.composeapp.generated.resources.notification_sound_page
import multiplatformtalk.composeapp.generated.resources.notification_sound_pencil1
import multiplatformtalk.composeapp.generated.resources.notification_sound_bubble_1
import multiplatformtalk.composeapp.generated.resources.notification_sound_typing_1
import multiplatformtalk.composeapp.generated.resources.notification_sound_typing
import multiplatformtalk.composeapp.generated.resources.notification_sound_tick_2
import multiplatformtalk.composeapp.generated.resources.notification_sound_tick_1
import multiplatformtalk.composeapp.generated.resources.notification_sound_drum
import multiplatformtalk.composeapp.generated.resources.notification_sound_water_step
import multiplatformtalk.composeapp.generated.resources.notification_sound_page2
import multiplatformtalk.composeapp.generated.resources.notification_sound_ooph
import multiplatformtalk.composeapp.generated.resources.notification_sound_button_2
import multiplatformtalk.composeapp.generated.resources.notification_sound_yank_1
import multiplatformtalk.composeapp.generated.resources.notification_sound_yank_2
import multiplatformtalk.composeapp.generated.resources.notification_sound_bell_little
import multiplatformtalk.composeapp.generated.resources.notification_sound_dog
import org.jetbrains.compose.resources.StringResource

class SettingNotificationSoundScreen : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: SettingViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()

        DisposableEffect(Unit) {
            onDispose { viewModel.onAction(SettingActions.StopSoundPreview) }
        }

        OverlayScaffold(title = stringResource(Res.string.setting_noti_sound_title)) { padding ->
            NotificationSoundBody(
                padding = padding,
                selectedSoundId = uiState.notification.soundId,
                onSelect = { id ->
                    viewModel.onAction(SettingActions.SetNotificationSoundId(id))
                    viewModel.onAction(SettingActions.PlaySoundPreview(id))
                }
            )
        }
    }
}

@Composable
private fun NotificationSoundBody(
    padding: PaddingValues,
    selectedSoundId: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        NotificationSoundCatalog.SOUNDS.indices.forEach { id ->
            SettingItemRadio(
                title = notificationSoundLabel(id),
                selected = id == selectedSoundId,
                onClick = { onSelect(id) }
            )
            LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationSoundBodyPreview() {
    MaterialTheme {
        NotificationSoundBody(padding = PaddingValues(0.dp), selectedSoundId = 0, onSelect = {})
    }
}

/** 알림음 이름 — [NotificationSoundCatalog.SOUNDS] 와 같은 순서. */
private val SOUND_NAMES: List<StringResource> = listOf(
    Res.string.notification_sound_ding,
    Res.string.notification_sound_button_1,
    Res.string.notification_sound_page,
    Res.string.notification_sound_pencil1,
    Res.string.notification_sound_bubble_1,
    Res.string.notification_sound_typing_1,
    Res.string.notification_sound_typing,
    Res.string.notification_sound_tick_2,
    Res.string.notification_sound_tick_1,
    Res.string.notification_sound_drum,
    Res.string.notification_sound_water_step,
    Res.string.notification_sound_page2,
    Res.string.notification_sound_ooph,
    Res.string.notification_sound_button_2,
    Res.string.notification_sound_yank_1,
    Res.string.notification_sound_yank_2,
    Res.string.notification_sound_bell_little,
    Res.string.notification_sound_dog,
)

/** 알림음 이름. 범위 밖이면 기본 알림음의 이름. */
@Composable
internal fun notificationSoundLabel(id: Int): String =
    stringResource(SOUND_NAMES.getOrElse(id) { SOUND_NAMES[NotificationSoundCatalog.DEFAULT_SOUND_ID] })

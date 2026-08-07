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
import com.eunilsung.talk.data.remote.push.NotificationSoundCatalog
import com.eunilsung.talk.ui.setting.component.SettingItemRadio
import com.eunilsung.talk.ui.setting.SettingActions
import com.eunilsung.talk.ui.setting.SettingViewModel
import com.eunilsung.talk.ui.uikit.line.LineDivider
import org.koin.compose.viewmodel.koinViewModel
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.setting_noti_sound_title
import org.jetbrains.compose.resources.stringResource

class SettingNotificationSoundScreen : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: SettingViewModel = koinViewModel()
        val noti by viewModel.notificationSettings.collectAsState()

        DisposableEffect(Unit) {
            onDispose { viewModel.stopSoundPreview() }
        }

        OverlayScaffold(title = stringResource(Res.string.setting_noti_sound_title)) { padding ->
            NotificationSoundBody(
                padding = padding,
                selectedSoundId = noti.soundId,
                onSelect = { id ->
                    viewModel.onAction(SettingActions.SetNotificationSoundId(id))
                    viewModel.playSoundPreview(id)
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
        NotificationSoundCatalog.SOUNDS.forEachIndexed { id, sound ->
            SettingItemRadio(
                title = sound.displayName,
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

internal fun notificationSoundLabel(id: Int): String =
    NotificationSoundCatalog.displayNameOrDefault(id)

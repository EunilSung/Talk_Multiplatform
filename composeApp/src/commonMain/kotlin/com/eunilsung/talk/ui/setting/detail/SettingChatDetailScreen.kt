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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.eunilsung.talk.domain.repository.ChatSettingsState
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.setting.SettingActions
import com.eunilsung.talk.ui.setting.SettingViewModel
import com.eunilsung.talk.ui.setting.component.SettingItemNavigate
import com.eunilsung.talk.ui.setting.component.SettingItemToggle
import com.eunilsung.talk.ui.setting.overlay.SettingFontSizeScreen
import com.eunilsung.talk.ui.uikit.line.LineDivider
import org.koin.compose.viewmodel.koinViewModel
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.setting_chat
import multiplatformtalk.composeapp.generated.resources.setting_chat_enter_send
import multiplatformtalk.composeapp.generated.resources.setting_font_size
import org.jetbrains.compose.resources.stringResource

class SettingChatDetailScreen : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: SettingViewModel = koinViewModel()
        val chat by viewModel.chatSettings.collectAsState()
        val showOverlay = LocalFullScreenOverlay.current

        SettingDetailScaffold(title = stringResource(Res.string.setting_chat)) { padding ->
            ChatDetailBody(
                padding = padding,
                state = chat,
                onAction = viewModel::onAction,
                onOpenFontSize = { showOverlay(SettingFontSizeScreen()) }
            )
        }
    }
}

@Composable
private fun ChatDetailBody(
    padding: PaddingValues,
    state: ChatSettingsState,
    onAction: (SettingActions) -> Unit,
    onOpenFontSize: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        SettingItemToggle(
            title = stringResource(Res.string.setting_chat_enter_send),
            checked = state.enterToSend,
            onCheckedChange = { onAction(SettingActions.SetChatEnterSend(it)) },
        )
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        SettingItemNavigate(
            title = stringResource(Res.string.setting_font_size),
            valueText = "${state.fontSize}pt",
            onClick = onOpenFontSize
        )
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatDetailBodyPreview() {
    MaterialTheme {
        ChatDetailBody(padding = PaddingValues(0.dp), state = ChatSettingsState(), onAction = {}, onOpenFontSize = {})
    }
}

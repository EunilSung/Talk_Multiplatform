package com.eunilsung.talk.ui.setting.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import com.eunilsung.talk.domain.repository.ChatSettingsRepository
import com.eunilsung.talk.ui.setting.SettingActions
import com.eunilsung.talk.ui.setting.SettingViewModel
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.line.LineDivider
import org.koin.compose.viewmodel.koinViewModel
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.setting_font_size
import multiplatformtalk.composeapp.generated.resources.font_size_preview
import org.jetbrains.compose.resources.stringResource

class SettingFontSizeScreen : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: SettingViewModel = koinViewModel()
        val chat by viewModel.chatSettings.collectAsState()

        OverlayScaffold(title = stringResource(Res.string.setting_font_size)) { padding ->
            FontSizeBody(
                padding = padding,
                selectedFontSize = chat.fontSize,
                onSelect = { viewModel.onAction(SettingActions.SetChatFontSize(it)) }
            )
        }
    }
}

@Composable
private fun FontSizeBody(
    padding: PaddingValues,
    selectedFontSize: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.font_size_preview),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = selectedFontSize.sp),
                color = AppColors.TextSub
            )
        }
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))

        ChatSettingsRepository.FONT_SIZE_OPTIONS.forEach { sp ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(sp) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${sp}pt",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = sp.sp),
                    modifier = Modifier.weight(1f)
                )
                RadioButton(
                    selected = sp == selectedFontSize,
                    onClick = null
                )
            }
            LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FontSizeBodyPreview() {
    MaterialTheme {
        FontSizeBody(
            padding = PaddingValues(0.dp),
            selectedFontSize = ChatSettingsRepository.DEFAULT_FONT_SIZE,
            onSelect = {}
        )
    }
}

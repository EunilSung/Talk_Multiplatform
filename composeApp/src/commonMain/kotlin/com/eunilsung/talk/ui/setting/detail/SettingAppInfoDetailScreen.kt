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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.eunilsung.talk.data.local.AppVersionProvider
import com.eunilsung.talk.ui.uikit.line.LineDivider
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.setting_app_info
import multiplatformtalk.composeapp.generated.resources.setting_app_version
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class SettingAppInfoDetailScreen : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        SettingDetailScaffold(title = stringResource(Res.string.setting_app_info)) { padding ->
            AppInfoDetailBody(padding)
        }
    }
}

@Composable
private fun AppInfoDetailBody(padding: PaddingValues) {
    val appVersionProvider: AppVersionProvider = koinInject()
    val versionName = remember { appVersionProvider.currentVersionName().ifBlank { "-" } }
    AppInfoDetailBody(padding, versionName)
}

@Composable
private fun AppInfoDetailBody(padding: PaddingValues, versionName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        SettingDetailRow(label = stringResource(Res.string.setting_app_version), value = versionName)
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun AppInfoDetailBodyPreview() {
    MaterialTheme {
        AppInfoDetailBody(PaddingValues(0.dp), versionName = "10.1.0")
    }
}

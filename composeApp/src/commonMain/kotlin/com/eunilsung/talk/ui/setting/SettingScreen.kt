package com.eunilsung.talk.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.setting
import multiplatformtalk.composeapp.generated.resources.setting_app_info
import multiplatformtalk.composeapp.generated.resources.setting_chat
import multiplatformtalk.composeapp.generated.resources.setting_notification
import multiplatformtalk.composeapp.generated.resources.setting_security
import com.eunilsung.talk.ui.main.LocalRightNavigator
import com.eunilsung.talk.ui.main.LocalRightPaneOpen
import com.eunilsung.talk.ui.setting.component.SettingItemText
import com.eunilsung.talk.ui.setting.detail.SettingAppInfoDetailScreen
import com.eunilsung.talk.ui.setting.detail.SettingChatDetailScreen
import com.eunilsung.talk.ui.setting.detail.SettingNotificationDetailScreen
import com.eunilsung.talk.ui.setting.detail.SettingSecurityDetailScreen
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.BackHandler
import com.eunilsung.talk.ui.uikit.line.LineDivider
import com.eunilsung.talk.ui.uikit.rememberMoveToBackground
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** 하단 탭의 마지막 화면 — 환경설정. */
object SettingScreen : Screen {
    @Composable
    override fun Content() {
        val rightNavigator = LocalRightNavigator.current
        SettingContent(onItemClick = { item -> openDetail(rightNavigator, item) })
    }
}

enum class SettingItem(val titleRes: StringResource) {
    SECURITY(Res.string.setting_security),
    NOTIFICATION(Res.string.setting_notification),
    CHAT(Res.string.setting_chat),
    APP_INFO(Res.string.setting_app_info)
}

private fun openDetail(rightNavigator: Navigator, item: SettingItem) {
    val newScreen: Screen = when (item) {
        SettingItem.SECURITY -> SettingSecurityDetailScreen()
        SettingItem.NOTIFICATION -> SettingNotificationDetailScreen()
        SettingItem.CHAT -> SettingChatDetailScreen()
        SettingItem.APP_INFO -> SettingAppInfoDetailScreen()
    }
    rightNavigator.popUntilRoot()
    rightNavigator.push(newScreen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingContent(
    onItemClick: (SettingItem) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val moveToBackground = rememberMoveToBackground()

    // 탭 최상위 화면이라 뒤로가기는 앱 종료가 아니라 백그라운드 전환.
    val rightPaneOpen = LocalRightPaneOpen.current
    BackHandler(enabled = !rightPaneOpen, onBack = { moveToBackground() })

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopBarV1(
                title = stringResource(Res.string.setting),
                scrollBehavior = scrollBehavior,
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Bg)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            SettingItem.entries.forEach { item ->
                SettingItemText(
                    title = stringResource(item.titleRes),
                    onClick = { onItemClick(item) }
                )
                LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingContentPreview() {
    MaterialTheme {
        SettingContent()
    }
}

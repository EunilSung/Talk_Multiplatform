package com.eunilsung.talk.ui.setting.overlay

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.uikit.topbar.BackScaffold

/** 전체화면 오버레이용 상세 Scaffold — 뒤로가기가 오버레이를 닫는다. */
@Composable
internal fun OverlayScaffold(
    title: String,
    content: @Composable (PaddingValues) -> Unit
) {
    val showOverlay = LocalFullScreenOverlay.current
    BackScaffold(title = title, onBack = { showOverlay(null) }, content = content)
}

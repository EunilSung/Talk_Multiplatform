package com.eunilsung.talk.ui.uikit.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 전체화면 동영상 플레이어 (Android: VideoView, iOS: AVPlayerViewController). */
@Composable
expect fun VideoPlayer(
    uri: String,
    modifier: Modifier = Modifier,
)

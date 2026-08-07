package com.eunilsung.talk.ui.uikit.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.play
import platform.AVFoundation.pause
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

/** AVPlayerViewController — 네이티브 재생. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(
    uri: String,
    modifier: Modifier,
) {
    val controller = remember(uri) {
        val url = if (uri.startsWith("/")) NSURL.fileURLWithPath(uri) else NSURL.URLWithString(uri)
        AVPlayerViewController().apply {
            player = url?.let { AVPlayer(uRL = it) }
        }
    }

    UIKitViewController(
        factory = { controller },
        modifier = modifier,
    )

    DisposableEffect(controller) {
        controller.player?.play()
        onDispose { controller.player?.pause() }
    }
}

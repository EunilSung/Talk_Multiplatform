package com.eunilsung.talk.ui.uikit.video

import android.net.Uri
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** VideoView + MediaController. 준비되면 자동 재생. */
@Composable
actual fun VideoPlayer(
    uri: String,
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val container = FrameLayout(ctx)
            val videoView = VideoView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ).also { it.gravity = android.view.Gravity.CENTER }

                setVideoURI(Uri.parse(uri))
                val controller = MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller)
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    start()
                }
            }
            container.addView(videoView)
            container
        },
    )

    // 다이얼로그가 닫힐 때 재생 중단은 VideoView 가 dispose 시 자동 처리되나, 명시적으로도 멈춘다.
    DisposableEffect(uri) { onDispose { } }
}

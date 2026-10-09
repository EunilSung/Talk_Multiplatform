package com.eunilsung.talk.ui.uikit.emoticon

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import multiplatformtalk.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * 내장 움직이는 이모티콘을 그린다.
 *
 * @param playCount 몇 번 재생하고 마지막 장면에서 멈출지. 0 이면 멈추지 않는다(파일의 반복 횟수는 보지 않는다)
 * @param restartKey 바뀌면 처음부터 다시 [playCount] 번 재생한다
 */
@Composable
expect fun AnimatedEmoticonImage(
    resourceName: String,
    contentDescription: String?,
    modifier: Modifier,
    playCount: Int = 0,
    restartKey: Int = 0,
)

/**
 * 프레임마다 정해진 시간([frameDurationsMs])만큼 [showFrame] 으로 보여 주며 [playCount] 번 돌린다. 0 이면 멈추지 않는다.
 * 다 돌면 마지막 프레임을 제 시간만큼 보여 준 뒤 끝난다 — 그 장면에 멈춰 있게 된다. 한 장짜리는 돌리지 않는다.
 */
internal suspend fun playGifFrames(frameDurationsMs: List<Int>, playCount: Int, showFrame: (Int) -> Unit) {
    if (frameDurationsMs.size <= 1) return
    var played = 0
    while (true) {
        frameDurationsMs.forEachIndexed { index, durationMs ->
            showFrame(index)
            delay(durationMs.toLong())
        }
        played++
        if (playCount in 1..played) return
    }
}

/**
 * 내장 이모티콘의 정지 그림. 읽고 푸는 동안에도 [modifier] 크기만큼 자리를 차지한다 — 대화방 말풍선이 0 높이로 그려지면
 * 플링 한 번에 이모티콘 수십 개를 지나간다.
 */
@Composable
fun StaticEmoticonImage(
    resourceName: String,
    contentDescription: String?,
    modifier: Modifier,
) {
    val pngPath = "drawable/$resourceName.png"
    val bitmap by produceState<ImageBitmap?>(null, pngPath) {
        value = runCatching { Res.readBytes(pngPath).decodeToImageBitmap() }.getOrNull()
    }
    val loaded = bitmap
    if (loaded == null) {
        Box(modifier)
    } else {
        Image(
            bitmap = loaded,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}

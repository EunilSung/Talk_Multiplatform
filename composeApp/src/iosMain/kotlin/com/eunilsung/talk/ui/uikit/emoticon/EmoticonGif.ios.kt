package com.eunilsung.talk.ui.uikit.emoticon

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import multiplatformtalk.composeapp.generated.resources.Res
import kotlinx.coroutines.delay
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image as SkiaImage

/** 디코딩된 GIF 한 프레임 — 이미지 + 표시 시간(ms). */
private data class GifFrame(val image: ImageBitmap, val durationMs: Int)

@Composable
actual fun AnimatedEmoticonImage(
    resourceName: String,
    contentDescription: String?,
    modifier: Modifier,
) {
    val gifPath = "drawable/$resourceName.gif"

    val frames by produceState<List<GifFrame>>(emptyList(), gifPath) {
        value = runCatching { decodeGifFrames(Res.readBytes(gifPath)) }.getOrElse { emptyList() }
    }

    if (frames.isEmpty()) {
        StaticEmoticonImage(resourceName, contentDescription, modifier)
        return
    }

    var index by remember(frames) { mutableStateOf(0) }
    LaunchedEffect(frames) {
        if (frames.size <= 1) return@LaunchedEffect
        while (true) {
            delay(frames[index].durationMs.toLong())
            index = (index + 1) % frames.size
        }
    }

    Image(
        bitmap = frames[index.coerceIn(0, frames.lastIndex)].image,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/** Skiko [Codec] 로 GIF 전 프레임을 순차 디코딩. */
private fun decodeGifFrames(bytes: ByteArray): List<GifFrame> {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val count = codec.frameCount
    val infos = codec.framesInfo
    val bitmap = Bitmap()
    bitmap.allocPixels(codec.imageInfo)
    return (0 until count).map { i ->
        codec.readPixels(bitmap, i)
        val image = SkiaImage.makeFromBitmap(bitmap).toComposeImageBitmap()
        val duration = infos.getOrNull(i)?.duration?.takeIf { it > 0 } ?: 100
        GifFrame(image, duration)
    }
}

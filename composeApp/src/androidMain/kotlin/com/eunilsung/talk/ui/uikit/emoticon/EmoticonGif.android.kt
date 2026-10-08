package com.eunilsung.talk.ui.uikit.emoticon

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.gif.MovieDrawable
import coil3.gif.repeatCount
import coil3.request.ImageRequest
import multiplatformtalk.composeapp.generated.resources.Res

/** 움직이는 이모티콘 전용 GIF [ImageLoader] — 1회 생성 후 전역 캐시. */
private var cachedGifLoader: ImageLoader? = null

/**
 * coil 의 `repeatCount` 는 처음 한 번을 뺀 **추가** 반복 횟수다(0 = 한 번). [restartKey] 가 바뀌면 이미지를 새로 만들어 처음부터
 * 재생한다 — 같은 바이트·같은 횟수의 요청은 coil 이 같은 요청으로 보고 다시 불러오지 않는다.
 */
@Composable
actual fun AnimatedEmoticonImage(
    resourceName: String,
    contentDescription: String?,
    modifier: Modifier,
    playCount: Int,
    restartKey: Int,
) {
    val context = LocalPlatformContext.current
    val loader = cachedGifLoader ?: ImageLoader.Builder(context)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
        .also { cachedGifLoader = it }

    val gifPath = "drawable/$resourceName.gif"
    val bytes by produceState<ByteArray?>(null, gifPath) {
        value = runCatching { Res.readBytes(gifPath) }.getOrNull()
    }

    val gif = bytes
    if (gif != null) {
        key(restartKey) {
            val request = remember(gif, playCount) {
                ImageRequest.Builder(context)
                    .data(gif)
                    .repeatCount(if (playCount > 0) playCount - 1 else MovieDrawable.REPEAT_INFINITE)
                    .build()
            }
            AsyncImage(
                model = request,
                imageLoader = loader,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = modifier,
            )
        }
    } else {
        StaticEmoticonImage(resourceName, contentDescription, modifier)
    }
}

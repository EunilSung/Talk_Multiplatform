package com.eunilsung.talk.ui.uikit.emoticon

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import multiplatformtalk.composeapp.generated.resources.Res

/** 움직이는 이모티콘 전용 GIF [ImageLoader] — 1회 생성 후 전역 캐시. */
private var cachedGifLoader: ImageLoader? = null

@Composable
actual fun AnimatedEmoticonImage(
    resourceName: String,
    contentDescription: String?,
    modifier: Modifier,
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
        AsyncImage(
            model = gif,
            imageLoader = loader,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        StaticEmoticonImage(resourceName, contentDescription, modifier)
    }
}

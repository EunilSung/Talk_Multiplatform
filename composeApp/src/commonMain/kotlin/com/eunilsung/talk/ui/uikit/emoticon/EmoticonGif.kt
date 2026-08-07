package com.eunilsung.talk.ui.uikit.emoticon

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import multiplatformtalk.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.decodeToImageBitmap

@Composable
expect fun AnimatedEmoticonImage(
    resourceName: String,
    contentDescription: String?,
    modifier: Modifier,
)

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
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}

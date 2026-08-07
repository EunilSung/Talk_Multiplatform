package com.eunilsung.talk.ui.uikit.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.eunilsung.talk.ui.uikit.click.clickable

@Composable
fun UrlImage(
    url: String,
    modifier: Modifier = Modifier.size(50.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(Color(0xFFDFE2E6)),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
    ){
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val imageModifier = if (onClick != null || onLongClick != null) {
            Modifier.fillMaxSize().clickable(onClick = onClick ?: {}, onLongClick = onLongClick)
        } else {
            Modifier.fillMaxSize()
        }
        AsyncImage(
            model = url,
            contentDescription = "Url Load Image",
            modifier = imageModifier,
            contentScale = ContentScale.Crop,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Success -> {}
                    else -> {}
                }
            }
        )
    }
}
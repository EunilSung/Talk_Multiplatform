package com.eunilsung.talk.ui.uikit.emoticon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.close_icon
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.pointer.blockPointerInput
import org.jetbrains.compose.resources.painterResource

@Composable
fun EmoticonPreview(
    previewEmoticon: EmoticonItem?,
    modifier: Modifier,
    onDismiss: () -> Unit
    ){
    previewEmoticon?.let { item ->
        Column(modifier) {
            Box(
                // 프리뷰가 대화 목록 위에 겹치므로 탭이 뒤쪽 말풍선으로 새지 않게 막는다.
                modifier = Modifier
                    .size(width = 200.dp, height = 180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.BgSub.copy(alpha = 0.5f))
                    .blockPointerInput(),
            ) {
                val imageModifier = Modifier.align(Alignment.Center).size(120.dp)
                if (item.isAnimated) {
                    AnimatedEmoticonImage(
                        resourceName = item.resourceName,
                        contentDescription = "emoticon preview",
                        modifier = imageModifier,
                    )
                } else {
                    Image(
                        painter = painterResource(item.resource),
                        contentDescription = "emoticon preview",
                        modifier = imageModifier,
                    )
                }
                IconButton(
                    onClick = { onDismiss() },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.close_icon),
                        contentDescription = "close preview",
                        modifier = Modifier.size(20.dp),
                        tint = AppColors.TextSub,
                    )
                }
            }
            Spacer(modifier = Modifier.size(10.dp))
        }

    }

}
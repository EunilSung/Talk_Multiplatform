package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.eunilsung.talk.data.local.VideoThumb
import com.eunilsung.talk.data.local.VideoThumbnailLoader
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.mediapicker.formatMediaDuration
import com.eunilsung.talk.util.ImageSizeUtils
import org.koin.compose.koinInject

/** 동영상 대화 말풍선 — 첫 프레임 + 중앙 재생버튼 + 재생시간. 탭하면 전체화면 재생. */
@Composable
fun VideoItem(
    itemProps: ChatItemProps,
) {
    val chat = itemProps.chat
    val source = chat.localPath.ifBlank { chat.imagePath }
    if (source.isBlank()) return

    val loader = koinInject<VideoThumbnailLoader>()
    val thumb by produceState(VideoThumb(), source) {
        value = runCatching { loader.load(source) }.getOrDefault(VideoThumb())
    }

    // 이미지와 동일한 크기 규칙. 해상도를 모르면 200x150.
    val parsed = ImageSizeUtils.getImageSize(thumb.widthHeight)
    val sizeBase = if (parsed != null) {
        Modifier.size(width = parsed.width, height = parsed.height)
    } else {
        Modifier.size(width = 200.dp, height = 150.dp)
    }
    val sizeModifier = if (itemProps.maxBubbleWidth != Dp.Unspecified) {
        sizeBase.widthIn(max = itemProps.maxBubbleWidth)
    } else sizeBase

    val showProgress = chat.isMe &&
        chat.chatStatue == Chat.Statue.SENDING &&
        chat.uploadProgress in 0..100

    ChatBubbleRow(itemProps) {
        Box(
            modifier = sizeModifier
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.Black)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = itemProps.onImageClick,
                    onLongClick = itemProps.onLongClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // 첫 프레임. 못 뽑으면 어두운 배경만 남는다.
            thumb.frameBytes?.let { bytes ->
                AsyncImage(
                    model = bytes,
                    contentDescription = "Video first frame",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AppColors.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "▶", color = AppColors.White, fontSize = 20.sp, lineHeight = 20.sp)
                }
                Spacer(Modifier.size(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatMediaDuration(thumb.durationSec),
                        color = AppColors.White,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (showProgress) {
                UploadProgressOverlay(
                    progress = chat.uploadProgress,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoItemPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .size(200.dp, 150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = AppColors.White, fontSize = 20.sp) }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp),
            ) { Text("1:23", color = AppColors.White, fontSize = 11.sp) }
        }
    }
}

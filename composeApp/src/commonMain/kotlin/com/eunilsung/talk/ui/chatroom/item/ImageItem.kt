package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.ui.uikit.image.UrlImage
import com.eunilsung.talk.util.ImageSizeUtils

@Composable
fun ImageItem(
    itemProps: ChatItemProps
) {
    if (itemProps.chat.imagePath == "") return

    val parsed = ImageSizeUtils.getImageSize(itemProps.chat.imageSize)
    val baseSizeModifier = if (parsed != null) {
        Modifier.sizeIn(maxWidth = parsed.width, maxHeight = parsed.height)
    } else {
        Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp)
    }
    val sizeModifier = if (itemProps.maxBubbleWidth != Dp.Unspecified) {
        baseSizeModifier.widthIn(max = itemProps.maxBubbleWidth)
    } else baseSizeModifier

    val isSending = itemProps.chat.chatStatue == Chat.Statue.SENDING
    val previewUrl = if (isSending && itemProps.chat.localPath.isNotBlank()) {
        itemProps.chat.localPath
    } else {
        itemProps.chat.imagePath
    }

    val showProgress = itemProps.chat.isMe &&
        isSending &&
        itemProps.chat.uploadProgress in 0..100

    ChatBubbleRow(itemProps) {
        Box {
            UrlImage(
                url = previewUrl,
                modifier = sizeModifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFDFE2E6)),
                onClick = itemProps.onImageClick,
                onLongClick = itemProps.onLongClick,
            )
            if (showProgress) {
                UploadProgressOverlay(
                    progress = itemProps.chat.uploadProgress,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ImageItemPreview() {
    MaterialTheme {
        ImageItem(
            ChatItemProps(
                chat = Chat.Item(
                    imagePath = "sample.png",
                    imageSize = "200:150",
                    chatStatue = Chat.Statue.COMPLETE,
                ),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ImageItemUploadingPreview() {
    MaterialTheme {
        ImageItem(
            ChatItemProps(
                chat = Chat.Item(
                    imagePath = "sample.png",
                    imageSize = "200:150",
                    chatStatue = Chat.Statue.SENDING,
                    uploadProgress = 65,
                ),
            ),
        )
    }
}

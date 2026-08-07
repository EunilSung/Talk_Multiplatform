package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.ui.uikit.emoticon.AnimatedEmoticonImage
import com.eunilsung.talk.ui.uikit.emoticon.emoticonResourceForId
import com.eunilsung.talk.ui.uikit.emoticon.emoticonResourceNameForId
import com.eunilsung.talk.ui.uikit.emoticon.isAnimatedEmoticonName
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.tooling.preview.Preview

private val EmoticonChatSize = 120.dp

@Composable
fun ChatEmoticonItem(
    itemProps: ChatItemProps
) {
    val id = itemProps.chat.emoticon.id
    if (id.isEmpty()) return
    val resourceName = emoticonResourceNameForId(id) ?: return

    val imageModifier = Modifier
        .size(EmoticonChatSize)
        .combinedClickable(
            onClick = {},
            onLongClick = itemProps.onLongClick,
        )

    ChatBubbleRow(itemProps) {
        if (isAnimatedEmoticonName(resourceName)) {
            AnimatedEmoticonImage(
                resourceName = resourceName,
                contentDescription = id,
                modifier = imageModifier,
            )
        } else {
            val resource = emoticonResourceForId(id) ?: return@ChatBubbleRow
            Image(
                painter = painterResource(resource),
                contentDescription = id,
                modifier = imageModifier,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatEmoticonItemPreview() {
    MaterialTheme {
        ChatEmoticonItem(ChatItemProps())
    }
}

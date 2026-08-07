package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun TextItem(
    itemProps: ChatItemProps
) {
    if (itemProps.chat.chatContent.isEmpty()) return
    val previewUrl = remember(itemProps.chat.chatContent) { extractFirstUrl(itemProps.chat.chatContent) }

    @Composable
    fun BubbleColumn(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .background(color = itemProps.bubbleBgColor, shape = itemProps.bubbleShape())
                .combinedClickable(
                    onClick = {},
                    onLongClick = itemProps.onLongClick,
                )
        ) {
            ExpandableChatText(
                text = itemProps.chat.chatContent,
                searchQuery = itemProps.searchQuery,
                color = itemProps.bubbleTextColor,
                fontSize = itemProps.chatFontSize,
                onLongClick = itemProps.onLongClick,
                modifier = Modifier.padding(vertical = 5.dp, horizontal = 10.dp)
            )
        }
    }

    if (previewUrl == null) {
        ChatBubbleRow(itemProps) {
            BubbleColumn(modifier = itemProps.bubbleWidthCap())
        }
    } else {
        Column(
            horizontalAlignment = if (itemProps.isMe) Alignment.End else Alignment.Start,
        ) {
            BubbleColumn(modifier = itemProps.bubbleWidthCap())
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                if (itemProps.isMe) ChatDateSlot(itemProps)
                LinkPreviewCard(url = previewUrl)
                if (!itemProps.isMe) ChatDateSlot(itemProps)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TextItemPreview() {
    MaterialTheme {
        TextItem(ChatItemProps())
    }
}

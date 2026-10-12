package com.eunilsung.talk.ui.chatroom.item

import org.jetbrains.compose.resources.stringResource
import multiplatformtalk.composeapp.generated.resources.bookmark_title
import com.eunilsung.talk.ui.util.chatTimeText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.bookmark_bubble_icon
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun ChatDateSlot(
    itemProps: ChatItemProps
) {
    Row(
        modifier = Modifier.padding(horizontal = 5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (itemProps.isBookmarked) {
            BookmarkBubbleIcon()
            Spacer(modifier = Modifier.width(2.dp))
        }
        Column(
            horizontalAlignment = if (itemProps.isMe) Alignment.End else Alignment.Start,
        ) {
            if (itemProps.isMe) {
                if (itemProps.chat.chatStatus == Chat.Status.COMPLETE) {
                    UnreadCountLabel(itemProps.chat.unReadCount)
                }
                if (itemProps.showTime || itemProps.chat.chatStatus != Chat.Status.COMPLETE) {
                    ChatSendStatueSlot(
                        status = itemProps.chat.chatStatus,
                        displayDate = chatTimeText(itemProps.chat.date),
                        onResend = itemProps.onResend,
                        onDelete = itemProps.onDelete,
                        modifier = Modifier,
                    )
                }
            } else {
                UnreadCountLabel(itemProps.chat.unReadCount)
                if (itemProps.showTime) {
                    ChatDateText(modifier = Modifier, date = chatTimeText(itemProps.chat.date))
                }
            }
        }
    }
}

@Composable
private fun BookmarkBubbleIcon() {
    Icon(
        painter = painterResource(Res.drawable.bookmark_bubble_icon),
        contentDescription = stringResource(Res.string.bookmark_title),
        tint = AppColors.TextSub,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun UnreadCountLabel(unReadCount: String) {
    val n = unReadCount.toIntOrNull() ?: 0
    if (n <= 0) return
    Text(
        text = n.toString(),
        color = AppColors.Main,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 12.sp,
        maxLines = 1
    )
}

@Composable
private fun ChatDateText(modifier: Modifier, date: String) {
    Text(
        text = date,
        color = AppColors.TextSub,
        fontSize = 10.sp,
        modifier = modifier,
        lineHeight = 10.sp,
        maxLines = 2
    )
}

@Composable
private fun ChatSendStatueSlot(
    status: String,
    displayDate: String,
    onResend: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    when (status) {
        Chat.Status.SENDING -> {
            CircularProgressIndicator(
                modifier = modifier.size(12.dp),
                color = AppColors.PrimaryMain,
                strokeWidth = 1.5.dp,
            )
        }
        Chat.Status.FAIL -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "↻",
                    color = Color.Red,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onResend() }
                        .wrapContentSize(Alignment.Center),
                )
                Text(
                    text = "✕",
                    color = AppColors.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onDelete() }
                        .wrapContentSize(Alignment.Center),
                )
            }
        }
        else -> {
            ChatDateText(modifier = modifier, date = displayDate)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatDateSlotPreview() {
    MaterialTheme {
        ChatDateSlot(ChatItemProps(isMe = true, showTime = true, isBookmarked = true))
    }
}

package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_recalled
import multiplatformtalk.composeapp.generated.resources.close_icon
import multiplatformtalk.composeapp.generated.resources.exclamation_mark_icon
import multiplatformtalk.composeapp.generated.resources.id
import multiplatformtalk.composeapp.generated.resources.ok
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun RecallItem(
    chat: Chat.Item = Chat.Item(),
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val isMe = chat.isMe
    val chatBgColor = if (isMe) AppColors.ChatMeBg else AppColors.ChatOtherBg

    Row(
        modifier = modifier
            .background(
                color = chatBgColor,
                shape = RoundedCornerShape(
                    topStart = if (isMe) 5.dp else 0.dp,
                    topEnd = if (isMe) 0.dp else 5.dp,
                    bottomStart = 5.dp,
                    bottomEnd = 5.dp
                )
            )
                .combinedClickable(
                    onClick = { onClick() },
                   onLongClick = { onLongClick() }
                ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            painter = painterResource(Res.drawable.exclamation_mark_icon),
            modifier = Modifier.padding(start = 10.dp).size(18.dp),
            contentDescription = "Mark Icon",
            tint = Color.Unspecified
        )

        Text(
            text = stringResource(Res.string.chat_recalled),
            color = if (isMe) AppColors.White else AppColors.Text,
            fontSize = 13.sp,
            maxLines = 20,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp, bottom = 5.dp, start = 5.dp, end = 10.dp)
        )

    }
}

@Preview(showBackground = true)
@Composable
fun RecallItemPreview() {
    MaterialTheme {
        RecallItem(chat = Chat.Item())
    }
}
package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.ui.theme.AppColors

@Composable
fun InviteItem(
    chat: Chat.Item = Chat.Item(),
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = AppColors.Dark.TextDisabled,
            shape = CircleShape
        ) {
            Text(
                text = chat.chatContent,
                modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun InviteItemPreview() {
    MaterialTheme {
        InviteItem(chat = Chat.Item(
            chatContent = "000님이 성은일님을 초대했습니다"
        ))
    }
}
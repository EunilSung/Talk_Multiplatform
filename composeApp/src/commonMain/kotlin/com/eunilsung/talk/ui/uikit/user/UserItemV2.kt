package com.eunilsung.talk.ui.uikit.user

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.chatroom.item.buildChatHighlightedText
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.clickable
import com.eunilsung.talk.ui.uikit.image.ProfileImages

/** @param leadingContent [content] 앞에 놓을 요소(예: 미리보기 썸네일). */
@Composable
fun UserItemV2(
    userId: String,
    name: String,
    content: String = "",
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = { },
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileImages(
            userIds = if (userId.isNotBlank()) listOf(userId) else emptyList(),
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.width(10.dp))

        Text(
            text = name.ifBlank { userId },
            color = AppColors.Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(10.dp))
        if (leadingContent != null) {
            leadingContent()
            if (content.isNotEmpty()) Spacer(Modifier.width(4.dp))
        }
        if (leadingContent == null || content.isNotEmpty()) {
            Text(
                text = buildChatHighlightedText(
                    text = content,
                    searchWord = "",
                    linkColor = AppColors.SkyLine,
                ),
                color = AppColors.TextSub,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UserItemV2Preview() {
    MaterialTheme {
        UserItemV2(
            userId = "",
            name = "성은일",
            content = "새로운 대화"
        )
    }
}

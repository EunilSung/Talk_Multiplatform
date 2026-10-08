package com.eunilsung.talk.ui.chatroom.item

import com.eunilsung.talk.ui.chatroom.input.replyToNameText
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ReplyChat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.uikit.line.LineDivider

@Composable
fun ReplyItem(
    itemProps: ChatItemProps
) {
    ChatBubbleRow(itemProps) {
        Column(
            modifier = itemProps.bubbleWidthCap()
                .width(IntrinsicSize.Max)
                .background(color = itemProps.bubbleBgColor, shape = itemProps.bubbleShape())
                .combinedClickable(
                    onClick = itemProps.onReplyOriginClick,
                    onLongClick = itemProps.onLongClick,
                )
        ) {
            Text(
                text = replyToNameText(itemProps.chat.replyChat.user.name),
                color = itemProps.bubbleTextColor,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp, bottom = 5.dp, start = 10.dp, end = 10.dp)
            )

            Text(
                text = itemProps.chat.replyChat.chatContent,
                color = itemProps.bubbleTextColor,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 0.dp, bottom = 10.dp, start = 10.dp, end = 10.dp)
            )

            if (itemProps.chat.chatContent.isNotEmpty()){
                LineDivider(modifier = Modifier.fillMaxWidth(), color = itemProps.bubbleTextColor)

                ExpandableChatText(
                    text = itemProps.chat.chatContent,
                    searchQuery = itemProps.searchQuery,
                    color = itemProps.bubbleTextColor,
                    fontSize = itemProps.chatFontSize,
                    onClick = itemProps.onReplyOriginClick,
                    onLongClick = itemProps.onLongClick,
                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 10.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReplyItemPreview() {
    MaterialTheme {
        ReplyItem(
            ChatItemProps(
                chat = Chat.Item(
                    chatContent = "본문의 글",
                    replyChat = ReplyChat(
                        chatContent = "답장의 글",
                        user = User(
                            name = "성은일에게 답장",
                            departmentName = "모바일팀",
                            positionName = "주임"
                        )
                    )
                )
            )
        )
    }
}

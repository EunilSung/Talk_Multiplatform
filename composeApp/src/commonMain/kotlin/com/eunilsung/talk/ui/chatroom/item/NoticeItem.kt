package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.Notice
import com.eunilsung.talk.ui.theme.AppColors
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.notice_added_msg
import multiplatformtalk.composeapp.generated.resources.notice_deleted_msg
import multiplatformtalk.composeapp.generated.resources.notice_view
import org.jetbrains.compose.resources.stringResource

/** 말풍선에 노출할 공지 본문 최대 줄 수 — 넘으면 말줄임 처리. 전문은 '글 확인하기'. */
private const val NOTICE_PREVIEW_MAX_LINES = 10

@Composable
fun NoticeItem(
    itemProps: ChatItemProps
) {
    val isDeleted = itemProps.chat.title == Notice.ACTION_DELETE

    ChatBubbleRow(
        itemProps = itemProps,
        modifier = Modifier
    ) {
        ChatCardBubble(itemProps) {
            ChatCardHeader(
                stringResource(
                    if (isDeleted) Res.string.notice_deleted_msg else Res.string.notice_added_msg
                )
            )
            Column(modifier = Modifier.padding(CardPadding)) {
                Text(
                    text = itemProps.chat.chatContent,
                    modifier = Modifier.padding(horizontal = 5.dp),
                    color = AppColors.Text,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = NOTICE_PREVIEW_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ChatCardActionRow(
                text = stringResource(Res.string.notice_view),
                onClick = itemProps.onNoticeClick,
                onLongClick = itemProps.onLongClick,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NoticeItemPreview() {
    MaterialTheme {
        NoticeItem(
            ChatItemProps(
                chat = Chat.Item(
                    title = Notice.ACTION_ADD,
                    chatContent = "이번 주 금요일 오후 3시에 전체 회의가 있습니다. 회의실은 3층 대회의실이며, " +
                        "각 팀별 진행 상황을 5분 내외로 공유해 주시기 바랍니다.",
                )
            )
        )
    }
}

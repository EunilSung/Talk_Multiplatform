package com.eunilsung.talk.ui.chatroom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_down_icon
import multiplatformtalk.composeapp.generated.resources.arrow_up_icon
import multiplatformtalk.composeapp.generated.resources.bookmark_icon
import multiplatformtalk.composeapp.generated.resources.chat_notice_icon
import multiplatformtalk.composeapp.generated.resources.notice_reopen_never
import multiplatformtalk.composeapp.generated.resources.notice_collapse
import multiplatformtalk.composeapp.generated.resources.notice_delete_label
import org.jetbrains.compose.resources.stringResource
import com.eunilsung.talk.domain.model.Notice
import com.eunilsung.talk.ui.chatroom.item.FullTextDialog
import com.eunilsung.talk.ui.chatroom.item.buildChatHighlightedText
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.line.LineDivider
import org.jetbrains.compose.resources.painterResource

@Composable
fun NoticeBarCard(
    notice: Notice,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    showDetails: Boolean = true,
    onShowDetailsChange: (Boolean) -> Unit = {},
    onToggle: () -> Unit,
    onHideToFab: () -> Unit,
    onHidePermanently: () -> Unit,
    onDelete: () -> Unit,
) {
    var showFullView by remember { mutableStateOf(false) }

    if (!expanded) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .shadow(elevation = 4.dp, shape = CircleShape)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AppColors.BgSub_2)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chat_notice_icon),
                    contentDescription = "공지 펼치기",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.BgSub_2,
        shadowElevation = 1.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chat_notice_icon),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(28.dp),
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                        .clickable { showFullView = true },
                    text = buildChatHighlightedText(
                        text = notice.content,
                        searchWord = "",
                        linkColor = AppColors.SkyLine,
                    ),
                    color = AppColors.Text,
                    fontSize = 13.sp,
                    maxLines = if (showDetails) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    painter = painterResource(
                        if (showDetails) Res.drawable.arrow_up_icon
                        else Res.drawable.arrow_down_icon
                    ),
                    contentDescription = if (showDetails) "상세 접기" else "상세 펼치기",
                    tint = AppColors.TextSub,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = { onShowDetailsChange(!showDetails) }),
                )
            }

            if (showDetails) {
                val authorLabel = buildAuthorLabel(notice)
                if (authorLabel.isNotBlank()) {
                    Text(
                        modifier = Modifier.padding(start = 54.dp, bottom = 8.dp),
                        text = authorLabel,
                        color = AppColors.TextSub,
                        fontSize = 11.sp,
                    )
                }

                LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))

                Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    NoticeActionButton(
                        modifier = Modifier.weight(1f).height(44.dp),
                        label = stringResource(Res.string.notice_reopen_never),
                        onClick = onHidePermanently,
                    )
                    LineDivider(modifier.width(1.dp).height(44.dp))
                    NoticeActionButton(
                        modifier = Modifier.weight(1f).height(44.dp),
                        label = stringResource(Res.string.notice_collapse),
                        onClick = onHideToFab,
                    )
                    LineDivider(modifier.width(1.dp).height(44.dp))
                    NoticeActionButton(
                        modifier = Modifier.weight(1f).height(44.dp),
                        label = stringResource(Res.string.notice_delete_label),
                        onClick = onDelete,
                    )
                }
            }
        }
    }

    if (showFullView) {
        FullTextDialog(
            text = notice.content,
            linkColor = AppColors.SkyLine,
            onDismiss = { showFullView = false },
        )
    }
}

@Composable
private fun NoticeActionButton(
    modifier: Modifier,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = AppColors.Text,
            fontSize = 13.sp,
        )
    }
}

private fun buildAuthorLabel(notice: Notice): String {
    val name = notice.ownerName.ifBlank { notice.ownerId }
    val pos = notice.ownerPosition
    return when {
        name.isBlank() && pos.isBlank() -> ""
        pos.isBlank() -> "($name)"
        else -> "($name/$pos)"
    }
}

@Preview(showBackground = true)
@Composable
fun NoticeBarCard1Preview() {
    NoticeBarCard(
        notice = Notice(
            noticeId = "1",
            chatRoomId = "1",
            content = "[공지] 공지입니다. 여기 작성된 내용은 테스트",
            ownerId = "1",
            ownerName = "성은일",
            ownerPosition = "선임",
            date = "2026"
        ),
        expanded = true,
        onToggle = { },
        onHideToFab = { },
        onHidePermanently = { },
        onDelete = { },
    )
}

@Preview(showBackground = true)
@Composable
fun NoticeBarCard2Preview() {
    NoticeBarCard(
        notice = Notice(
            noticeId = "1",
            chatRoomId = "1",
            content = "공지 내용",
        ),
        expanded = false,
        onToggle = { },
        onHideToFab = { },
        onHidePermanently = { },
        onDelete = { },
    )
}

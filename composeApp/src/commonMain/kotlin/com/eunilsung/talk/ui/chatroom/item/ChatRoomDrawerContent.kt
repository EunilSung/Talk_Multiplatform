package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.Config
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.drawer_participants
import multiplatformtalk.composeapp.generated.resources.notice
import multiplatformtalk.composeapp.generated.resources.notice_icon
import multiplatformtalk.composeapp.generated.resources.plus_bold_icon
import multiplatformtalk.composeapp.generated.resources.vote
import multiplatformtalk.composeapp.generated.resources.vote_icon
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.itemClickable
import com.eunilsung.talk.ui.uikit.drawer.DrawerCard
import com.eunilsung.talk.ui.uikit.drawer.DrawerSectionHeader
import com.eunilsung.talk.ui.uikit.user.UserItemV3
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 대화방 우측 드로어 — 메뉴 카드(공지/투표) + 참여자 카드. */
@Composable
fun ChatRoomDrawerContent(
    users: List<User>,
    onVoteClick: () -> Unit,
    onNoticeClick: () -> Unit,
    onInviteClick: () -> Unit,
    onUserClick: (String) -> Unit,
) {
    val myId = Config.MyInfo.userId
    Column(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(AppColors.Gray100BgAuto)
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        DrawerCard {
            DrawerMenuRow(
                icon = painterResource(Res.drawable.notice_icon),
                label = stringResource(Res.string.notice),
                onClick = onNoticeClick,
            )
            if (Config.ChatRoom.IS_VOTE_ENABLED) {
                DrawerMenuRow(
                    icon = painterResource(Res.drawable.vote_icon),
                    label = stringResource(Res.string.vote),
                    onClick = onVoteClick,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        DrawerCard(modifier = Modifier.weight(1f, fill = false)) {
            LazyColumn(
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
            ) {
                item {
                    DrawerSectionHeader(
                        label = stringResource(Res.string.drawer_participants),
                        count = users.size,
                        trailingIcon = painterResource(Res.drawable.plus_bold_icon),
                        trailingContentDescription = "",
                        onClick = onInviteClick,
                    )
                }
                items(users, key = { it.id }) { user ->
                    val subtitle = listOfNotNull(
                        user.departmentName?.takeIf { it.isNotBlank() },
                        user.positionName?.takeIf { it.isNotBlank() },
                    ).joinToString("/")
                    UserItemV3(
                        userId = user.id,
                        name = user.name,
                        subtitle = subtitle.ifBlank { null },
                        isMe = myId.isNotBlank() && user.id == myId,
                        onClick = { onUserClick(user.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

/** 카드 안의 메뉴 한 줄 — 아이콘 + 라벨. */
@Composable
private fun DrawerMenuRow(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .itemClickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            modifier = Modifier.size(24.dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = AppColors.Text,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatRoomDrawerContentPreview() {
    MaterialTheme {
        ChatRoomDrawerContent(
            users = listOf(
                User(id = "1", name = "이지은", departmentName = "연구팀", positionName = "팀장"),
                User(id = "2", name = "윤상희", departmentName = "연구팀", positionName = "프로"),
                User(id = "3", name = "이연진", departmentName = "연구팀", positionName = "프로"),
            ),
            onVoteClick = {},
            onNoticeClick = {},
            onInviteClick = {},
            onUserClick = {},
        )
    }
}

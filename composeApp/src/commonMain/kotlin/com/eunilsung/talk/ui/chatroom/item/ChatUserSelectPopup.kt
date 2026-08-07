package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.user.UserItemV3

/** @param selectedUserId 이 id 의 행을 선택 상태로 강조. */
@Composable
fun BoxScope.ChatUserSelectPopup(
    users: List<User>,
    onDismiss: () -> Unit,
    onUserClick: (User) -> Unit,
    selectedUserId: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    )

    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .widthIn(max = 400.dp)
            .heightIn(max = 400.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = shape
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(users, key = { it.id }) { user ->
                    val subtitle = listOfNotNull(
                        user.departmentName?.takeIf { it.isNotBlank() },
                        user.positionName?.takeIf { it.isNotBlank() },
                    ).joinToString("/")
                    // 배경을 padding 앞에 두어야 강조가 행 전체 폭을 덮는다.
                    val rowBackground =
                        if (user.id == selectedUserId) AppColors.UserSelectBg
                        else AppColors.Transparent
                    UserItemV3(
                        userId = user.id,
                        name = user.name,
                        subtitle = subtitle.ifBlank { null },
                        modifier = Modifier
                            .background(rowBackground)
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        onClick = { onUserClick(user) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatUserSelectPopupPreview() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ChatUserSelectPopup(
                users = listOf(
                    User(id = "1", name = "성은일", departmentName = "개발팀", positionName = "팀장"),
                    User(id = "2", name = "홍길동", departmentName = "기획팀"),
                    User(id = "3", name = "김철수"),
                ),
                onDismiss = {},
                onUserClick = {},
            )
        }
    }
}

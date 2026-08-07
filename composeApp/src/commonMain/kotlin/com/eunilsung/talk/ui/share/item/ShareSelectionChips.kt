package com.eunilsung.talk.ui.share.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.close_icon
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun SelectedUserChips(
    selectedUsers: List<User>,
    onRemove: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = selectedUsers,
            key = { it.id }
        ) { user ->
            Chip(
                label = user.name.ifBlank { user.id },
                onRemove = { onRemove(user.id) }
            )
        }
    }
}

@Composable
fun SelectedChatRoomChip(
    room: ChatRoom.Item,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Chip(
            label = room.displayTitle.ifBlank { "대화방" },
            onRemove = onRemove
        )
    }
}

@Composable
private fun Chip(
    label: String,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AppColors.Line,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = AppColors.Text,
                fontSize = 14.sp
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.close_icon),
                    contentDescription = "Remove",
                    tint = AppColors.TextSub,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectedUserChipsPreview() {
    MaterialTheme {
        SelectedUserChips(
            selectedUsers = listOf(
                User(id = "u1", name = "성은일"),
                User(id = "u2", name = "홍길동"),
            ),
            onRemove = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectedChatRoomChipPreview() {
    MaterialTheme {
        SelectedChatRoomChip(
            room = ChatRoom.Item(title = "개발팀 단톡방"),
            onRemove = {},
        )
    }
}

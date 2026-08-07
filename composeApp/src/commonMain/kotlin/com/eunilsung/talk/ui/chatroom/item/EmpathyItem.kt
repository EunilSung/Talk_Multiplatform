package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.empathy_0
import multiplatformtalk.composeapp.generated.resources.empathy_1
import multiplatformtalk.composeapp.generated.resources.empathy_2
import multiplatformtalk.composeapp.generated.resources.empathy_3
import multiplatformtalk.composeapp.generated.resources.empathy_4
import multiplatformtalk.composeapp.generated.resources.empathy_5
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.EmpathyChat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.itemClickable
import org.jetbrains.compose.resources.painterResource

private val EMPATHY_ICONS = listOf(
    Res.drawable.empathy_0, Res.drawable.empathy_1, Res.drawable.empathy_2,
    Res.drawable.empathy_3, Res.drawable.empathy_4, Res.drawable.empathy_5,
)

@Composable
fun EmpathyItem(
    chat: Chat.Item,
    modifier: Modifier = Modifier,
    onEmpathyClick: (typeIndex: Int) -> Unit = {},
    onEmpathyLongClick: (typeIndex: Int) -> Unit = {},
) {
    if (chat.chatType == Chat.Type.RECALL) return
    val empathy = chat.empathy
    if (empathy.chatID.isEmpty()) return

    val buckets = remember(empathy) {
        listOf(empathy.empathy0, empathy.empathy1, empathy.empathy2,
            empathy.empathy3, empathy.empathy4, empathy.empathy5)
    }
    val nonEmpty = buckets.withIndex().filter { it.value.isNotEmpty() }
    if (nonEmpty.isEmpty()) return

    Column {
        Spacer(modifier = Modifier.size(5.dp))
        Row(
            modifier = modifier
                .background(AppColors.BgChatEmpathy, RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            nonEmpty.forEach { (idx, users) ->
                Row(
                    modifier = Modifier
                        .itemClickable(
                            cornerRadius = 8.dp,
                            onClick = { onEmpathyClick(idx) },
                            onLongClick = { onEmpathyLongClick(idx) },
                        )
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(EMPATHY_ICONS[idx]),
                        contentDescription = "empathy $idx",
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = users.size.toString(),
                        modifier = Modifier.padding(start = 3.dp),
                        color = AppColors.Black,
                        fontSize = 13.sp,
                        lineHeight = 13.sp,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EmpathyItemPreview() {
    MaterialTheme {
        EmpathyItem(chat = Chat.Item(
            chatID = "1234567890",
            empathy = EmpathyChat(
                chatID = "1234567890",
                empathy0 = listOf(
                    User(id = "1", name = "사용자1")
                ),
                empathy1 = listOf(
                    User(id = "1", name = "사용자1")
                ),
                empathy2 = listOf(
                    User(id = "1", name = "사용자1")
                ),
                empathy3 = listOf(
                    User(id = "1", name = "사용자1")
                ),
                empathy4 = listOf(
                    User(id = "1", name = "사용자1")
                ),
                empathy5 = listOf(
                    User(id = "1", name = "사용자1")
                )

            )
        ))
    }
}
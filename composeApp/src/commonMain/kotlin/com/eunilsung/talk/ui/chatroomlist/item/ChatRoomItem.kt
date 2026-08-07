package com.eunilsung.talk.ui.chatroomlist.item

import com.eunilsung.talk.ui.util.chatRoomDateText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import multiplatformtalk.composeapp.generated.resources.no_chat_partner
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.alarm_off_icon
import multiplatformtalk.composeapp.generated.resources.pin_icon
import multiplatformtalk.composeapp.generated.resources.noti_chat
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.emoticon.StaticEmoticonImage
import com.eunilsung.talk.util.splitLeadingToken
import com.eunilsung.talk.ui.uikit.emoticon.emoticonResourceNameForId
import com.eunilsung.talk.ui.uikit.badge.BadgeV1
import com.eunilsung.talk.ui.uikit.checkbox.ToggleV1
import com.eunilsung.talk.ui.uikit.click.itemClickable
import com.eunilsung.talk.ui.uikit.image.NotificationAvatar
import com.eunilsung.talk.ui.uikit.image.ProfileImages
import com.eunilsung.talk.ui.uikit.line.LineDot
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatRoomItem(
    item: ChatRoom.Item,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier.heightIn(min = 70.dp, max = 100.dp),
    isToggleable: Boolean = false
){
    Column(modifier = Modifier) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (isToggleable && item.isSelect) AppColors.UserSelectBg else AppColors.Transparent)
                .itemClickable(
                    cornerRadius = 12.dp,
                    onClick = { onClick() },
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (isToggleable){
                ToggleV1(
                    checked = item.isSelect,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            if (item.id.contains("$")) {
                NotificationAvatar()
            } else {
                ProfileImages(userIds = item.displayProfileIds)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.displayTitle.ifBlank { stringResource(Res.string.no_chat_partner) },
                            color = AppColors.Text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (item.displayUserCount > 2) {
                            Text(
                                text = "${item.displayUserCount}",
                                color = AppColors.TextSub,
                                fontSize = 13.sp,
                                lineHeight = 13.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.size(2.dp))

                        if (item.isAlarm == "1"){
                            Icon(
                                painter = painterResource(Res.drawable.alarm_off_icon),
                                contentDescription = "Alarm Off",
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.TextDisabled
                            )
                        }

                        Spacer(modifier = Modifier.size(2.dp))

                        if (item.pinDate != "") {
                            Icon(
                                painter = painterResource(Res.drawable.pin_icon),
                                contentDescription = "Pin Icon",
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.TextDisabled
                            )
                        }
                    }

                    Text(
                        text = chatRoomDateText(item.lastChatDate),
                        color = AppColors.TextSub,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 미리보기 앞의 이모티콘 id 토큰은 이미지로 그리고 남은 본문은 옆에 붙인다.
                    val preview = item.lastChatContent.trim()
                    val parsed = remember(preview) {
                        val (token, rest) = splitLeadingToken(preview)
                        val name = token?.let { emoticonResourceNameForId(it) }
                        if (name != null) name to rest else null to preview
                    }
                    val (emoticonName, previewText) = parsed

                    if (emoticonName != null) {
                        StaticEmoticonImage(
                            resourceName = emoticonName,
                            contentDescription = null,
                            // 목록에서는 정적 이미지로만.
                            modifier = Modifier.height(22.dp),
                        )
                        if (previewText.isNotEmpty()) Spacer(modifier = Modifier.size(4.dp))
                    }
                    if (emoticonName == null || previewText.isNotEmpty()) {
                        Text(
                            text = previewText,
                            color = AppColors.TextSub,
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.size(10.dp))

                    val unReadCount = item.unReadCount.toIntOrNull() ?: 0
                    if (unReadCount > 0){
                        BadgeV1(count = unReadCount)
                    }
                }
            }
        }
        LineDot(modifier = Modifier
            .background(if (isToggleable && item.isSelect) AppColors.UserSelectBg else AppColors.Transparent)
            .padding(horizontal = 16.dp))
    }
}



@Preview(showBackground = true)
@Composable
fun ChatRoomItemPreview() {
    MaterialTheme {
        ChatRoomItem(
            item = ChatRoom.Item(
                lastChatDate = "오전 9:00",
                lastChatContent = "lastChatContent",
                title = "title",
                unReadCount = "9",
                isAlarm = "1",
                pinDate = "1"
            ),
            isToggleable = true
        )
    }
}
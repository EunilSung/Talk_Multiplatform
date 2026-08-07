package com.eunilsung.talk.ui.uikit.user

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.clickable
import com.eunilsung.talk.ui.uikit.image.ProfileImages
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.me_badge
import org.jetbrains.compose.resources.stringResource

/**
 * @param isMe true 면 이름 앞에 "나" 배지를 표시 (대화방 참여자 목록에서 본인 구분용).
 */
@Composable
fun UserItemV3(
    userId: String,
    name: String,
    subtitle: String? = null,
    isMe: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileImages(userIds = if (userId.isNotBlank()) listOf(userId) else emptyList())
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMe) {
                    MeBadge()
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = name.ifBlank { userId },
                    color = AppColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = AppColors.TextSub,
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 본인 표시용 원형 배지 — 이름 앞에 붙는다. */
@Composable
private fun MeBadge() {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(AppColors.Text),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(Res.string.me_badge),
            color = AppColors.BgAuto,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            maxLines = 1
        )
    }
}

@Preview(showBackground = true)
@Composable
fun UserItemV3Preview() {
    MaterialTheme {
        UserItemV3(
            userId = "",
            name = "성은일",
            subtitle = "개발팀/팀장",
        )
    }
}

package com.eunilsung.talk.ui.uikit.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.ui.theme.AppColors
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.noti_chat
import org.jetbrains.compose.resources.painterResource

/** 알림봇 대화방·대화의 아바타 — 사람 프로필이 없는 계정(`id` 에 `$` 포함)에 쓴다. */
@Composable
fun NotificationAvatar(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.noti_chat),
        contentDescription = null,
        modifier = modifier
            .size(50.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.Gray100BgAuto),
    )
}

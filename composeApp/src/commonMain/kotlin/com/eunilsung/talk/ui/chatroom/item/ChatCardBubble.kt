package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.line.LineDivider
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_right_icon
import org.jetbrains.compose.resources.painterResource

/** 카드형 말풍선(투표·공지) 본문 여백 — 하단 액션 행은 이 여백 밖에 두어 가장자리까지 채운다. */
internal val CardPadding = 10.dp

/** 카드 최대 폭 — 본문이 길어도 말풍선이 화면을 넘지 않게 한다. */
private val CardMaxWidth = 240.dp

/** 카드형 말풍선 껍데기 — 하단 액션 행이 둥근 모서리를 넘지 않도록 shape 로 잘라낸다. */
@Composable
internal fun ChatCardBubble(
    itemProps: ChatItemProps,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = itemProps
            .bubbleWidthCap()
            .widthIn(max = CardMaxWidth)
            .clip(itemProps.bubbleShape())
            .background(AppColors.BgSub)
            .combinedClickable(
                onClick = {},
                onLongClick = itemProps.onLongClick,
            ),
        content = content,
    )
}

/** 카드 상단 안내 문구 — 아래에 구분선을 함께 그린다. */
@Composable
internal fun ChatCardHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
        color = AppColors.TextSub,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    CardDivider()
}

/** 카드 하단 이동 행 — 구분선 + 회색 배경 + 우측 화살표. */
@Composable
internal fun ChatCardActionRow(
    text: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    CardDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Gray50Bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = AppColors.Text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            painter = painterResource(Res.drawable.arrow_right_icon),
            contentDescription = null,
            tint = AppColors.TextSub,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun CardDivider() {
    LineDivider(
        modifier = Modifier.fillMaxWidth().height(1.dp),
        color = AppColors.Line,
    )
}

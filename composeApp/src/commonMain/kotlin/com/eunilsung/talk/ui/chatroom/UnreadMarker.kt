package com.eunilsung.talk.ui.chatroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_read_check
import multiplatformtalk.composeapp.generated.resources.chat_summary_button
import multiplatformtalk.composeapp.generated.resources.chat_summary_loading
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.clickable
import org.jetbrains.compose.resources.stringResource

/**
 * 안읽은 대화가 시작되는 자리의 구분선.
 *
 * [onSummarize] 가 있으면 구분선 아래에 요약 버튼을 둔다. 요약을 받는 동안([isSummarizing])에는 눌리지 않는다.
 */
@Composable
fun UnreadMarker(
    isSummarizing: Boolean = false,
    onSummarize: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(AppColors.Dark.TextDisabled)
            )
            Text(
                text = stringResource(Res.string.chat_read_check),
                modifier = Modifier.padding(horizontal = 12.dp),
                fontSize = 12.sp,
                color = AppColors.Dark.TextDisabled
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(AppColors.Dark.TextDisabled)
            )
        }
        if (onSummarize != null) {
            Text(
                text = stringResource(
                    if (isSummarizing) Res.string.chat_summary_loading else Res.string.chat_summary_button
                ),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .alpha(if (isSummarizing) 0.6f else 1f)
                    .then(
                        if (isSummarizing) Modifier
                        else Modifier.clickable(cornerRadius = 14.dp, onClick = onSummarize)
                    )
                    .background(AppColors.Dark.TextDisabled, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 12.sp,
                color = Color.White
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UnreadMarkerPreview() {
    MaterialTheme {
        UnreadMarker(onSummarize = {})
    }
}

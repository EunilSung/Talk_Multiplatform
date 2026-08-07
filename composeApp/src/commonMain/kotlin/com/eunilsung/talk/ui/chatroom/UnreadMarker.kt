package com.eunilsung.talk.ui.chatroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_read_check
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.stringResource

@Composable
fun UnreadMarker() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp),
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
}

@Preview(showBackground = true)
@Composable
fun UnreadMarkerPreview() {
    MaterialTheme {
        UnreadMarker()
    }
}

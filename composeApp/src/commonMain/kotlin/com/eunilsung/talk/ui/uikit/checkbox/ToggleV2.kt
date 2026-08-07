package com.eunilsung.talk.ui.uikit.checkbox

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.theme.AppColors

/** 토글 스위치 — Material3 [Switch] 기반. */
@Composable
fun ToggleV2(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 35.dp,
    text: String = "",
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { onClick() },
            colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Main),
        )
        if (text.isNotEmpty()) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                color = AppColors.Text,
                fontSize = 15.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ToggleV2Preview() {
    MaterialTheme {
        Column {
            ToggleV2(checked = true, text = "켜짐")
            ToggleV2(checked = false, text = "꺼짐")
        }
    }
}

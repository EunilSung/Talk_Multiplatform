package com.eunilsung.talk.ui.uikit.checkbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.toggle_v1_dark_off
import multiplatformtalk.composeapp.generated.resources.toggle_v1_light_off
import multiplatformtalk.composeapp.generated.resources.toggle_v1_on
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.clickable
import org.jetbrains.compose.resources.painterResource

@Composable
fun ToggleV1(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    text: String = "",
    onClick: (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val offRes = if (isDark) Res.drawable.toggle_v1_dark_off else Res.drawable.toggle_v1_light_off

    Row(
        modifier = if (onClick != null) {
            modifier.clickable(rippleColor = null, onClick = onClick)
        } else {
            modifier
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(
                if (checked) Res.drawable.toggle_v1_on else offRes
            ),
            contentDescription = "toggle v1",
            modifier = Modifier.size(size),
        )

        if (text.isNotEmpty()) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                color = AppColors.Text,
                fontSize = 15.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ToggleV1Preview() {
    MaterialTheme {
        Column {
            ToggleV1(checked = true, text = "켜짐")
            ToggleV1(checked = false, text = "꺼짐")
        }
    }
}

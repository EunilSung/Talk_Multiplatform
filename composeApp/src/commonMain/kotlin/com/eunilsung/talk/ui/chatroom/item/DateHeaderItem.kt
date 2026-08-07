package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.theme.AppColors

@Composable
fun DateHeaderItem(
    dateText: String,
    isHighlighted: Boolean = false,
    triggerKey: Int = 0,
) {
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(isHighlighted, triggerKey) {
        if (!isHighlighted) return@LaunchedEffect
        val amplitudes = listOf(8f, -8f, 6f, -6f, 4f, -4f, 2f, -2f, 0f)
        amplitudes.forEach { amp ->
            offsetX.animateTo(amp, animationSpec = androidx.compose.animation.core.tween(durationMillis = 60))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = Dp(offsetX.value))
            .padding(bottom = 10.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = AppColors.Dark.TextDisabled,
            shape = CircleShape
        ) {
            Text(
                text = dateText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DateHeaderItemPreview() {
    MaterialTheme {
        DateHeaderItem(
            dateText = "2050-01-01"
        )
    }
}

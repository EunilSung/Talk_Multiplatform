package com.eunilsung.talk.ui.uikit.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.button.ButtonV1

@Composable
fun BadgeV1(
    count: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppColors.Red,
    contentColor: Color = Color.White
) {
    if (count <= 0) return

    val displayCount = if (count > 999) "999+" else count.toString()

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(color = backgroundColor, shape = CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayCount,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BadgeV1Preview() {
    MaterialTheme {
        BadgeV1(
            count = 1
        )
    }
}

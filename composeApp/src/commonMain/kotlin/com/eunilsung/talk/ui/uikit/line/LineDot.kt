package com.eunilsung.talk.ui.uikit.line

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.ui.theme.AppColors

@Composable
fun LineDot(
    modifier: Modifier = Modifier,
    color: Color = AppColors.Line,
    thickness: Dp = 1.5.dp,
    dashWidth: Dp = 3.5.dp,
    dashGap: Dp = 2.dp,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
    ) {
        val strokeWidthPx = thickness.toPx()
        val dashWidthPx = dashWidth.toPx()
        val dashGapPx = dashGap.toPx()
        var startX = 0f
        val y = size.height / 2

        while (startX < size.width) {
            drawLine(
                color = color,
                start = Offset(startX, y),
                end = Offset(startX + dashWidthPx, y),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Butt
            )
            startX += dashWidthPx + dashGapPx
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LineDotPreview() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxWidth().height(10.dp)){
            LineDot(modifier = Modifier.align(Alignment.Center))
        }
    }
}
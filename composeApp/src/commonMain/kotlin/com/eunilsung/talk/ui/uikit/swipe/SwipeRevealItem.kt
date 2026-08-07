package com.eunilsung.talk.ui.uikit.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.eunilsung.talk.ui.theme.AppColors

/** 스와이프로 아이템 뒤에서 드러나는 액션 버튼 정의. */
data class SwipeAction(
    val label: String,
    val background: Color,
    val textColor: Color = AppColors.White,
    val onClick: () -> Unit,
)

/** 왼쪽 스와이프 시 아이템 뒤에서 액션 버튼([actions])이 드러나는 리스트 아이템 래퍼. */
@Composable
fun SwipeRevealItem(
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    actionWidth: Dp = 84.dp,
    contentBackground: Color = AppColors.Bg,
    content: @Composable () -> Unit,
) {
    if (actions.isEmpty()) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val revealPx = with(density) { (actionWidth * actions.size).toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(modifier = modifier.fillMaxWidth().clipToBounds()) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End
        ) {
            actions.forEach { action ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(actionWidth)
                        .background(action.background)
                        .clickable {
                            scope.launch { offsetX.animateTo(0f) }
                            action.onClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = action.label,
                        color = action.textColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(contentBackground)
                .pointerInput(revealPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val target = (offsetX.value + dragAmount).coerceIn(-revealPx, 0f)
                            scope.launch { offsetX.snapTo(target) }
                        },
                        onDragEnd = {
                            val target = if (offsetX.value <= -revealPx / 2f) -revealPx else 0f
                            scope.launch { offsetX.animateTo(target) }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f) }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

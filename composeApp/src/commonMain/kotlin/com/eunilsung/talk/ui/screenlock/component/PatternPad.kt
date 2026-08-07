package com.eunilsung.talk.ui.screenlock.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.theme.AppColors
import kotlin.math.hypot

/** 직선상 두 노드 사이의 중간 노드 — 패턴이 가운데 노드를 자동 포함하도록. */
private val MIDPOINTS: Map<Set<Int>, Int> = mapOf(
    setOf(0, 2) to 1, setOf(3, 5) to 4, setOf(6, 8) to 7,
    setOf(0, 6) to 3, setOf(1, 7) to 4, setOf(2, 8) to 5,
    setOf(0, 8) to 4, setOf(2, 6) to 4,
)

private fun nodeCenter(index: Int, w: Float, h: Float): Offset {
    val col = index % 3
    val row = index / 3
    return Offset(w * (2 * col + 1) / 6f, h * (2 * row + 1) / 6f)
}

/**
 * 3x3 패턴 입력 그리드 — 드래그로 노드를 이어 그리고 손을 떼면 [onEntered] 호출.
 * [visibleTrail] = false 면 궤적을 숨기고, [errorKey] 변경 시 입력을 초기화한다.
 */
@Composable
fun PatternPad(
    title: String,
    subtitle: String,
    visibleTrail: Boolean,
    onEntered: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    subtitleColor: Color = AppColors.TextSub,
    errorKey: Int = 0,
) {
    val visited = remember { mutableStateListOf<Int>() }
    var current by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(errorKey) { visited.clear(); current = null }

    val mainColor = AppColors.Main
    val idleColor = AppColors.TextDisabled
    val trailColor = AppColors.Main.copy(alpha = 0.22f)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = AppColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(subtitle, color = subtitleColor, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .padding(8.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val radius = minOf(w, h) / 6f * 0.66f

                        fun addAt(pos: Offset) {
                            for (i in 0..8) {
                                if (i in visited) continue
                                if (hypot(pos.x - nodeCenter(i, w, h).x, pos.y - nodeCenter(i, w, h).y) <= radius) {
                                    val last = visited.lastOrNull()
                                    if (last != null) {
                                        MIDPOINTS[setOf(last, i)]?.let { mid ->
                                            if (mid !in visited) visited.add(mid)
                                        }
                                    }
                                    visited.add(i)
                                    break
                                }
                            }
                        }

                        val down = awaitFirstDown(requireUnconsumed = false)
                        visited.clear()
                        current = down.position
                        addAt(down.position)
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            current = change.position
                            addAt(change.position)
                            change.consume()
                        }
                        current = null
                        if (visited.isNotEmpty()) onEntered(visited.toList())
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val nodeR = minOf(w, h) / 9f * 0.30f
            val lineWidth = nodeR * 1.3f

            if (visibleTrail && visited.isNotEmpty()) {
                for (k in 0 until visited.size - 1) {
                    drawLine(
                        color = trailColor,
                        start = nodeCenter(visited[k], w, h),
                        end = nodeCenter(visited[k + 1], w, h),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round,
                    )
                }
                current?.let { c ->
                    visited.lastOrNull()?.let { last ->
                        drawLine(trailColor, nodeCenter(last, w, h), c, strokeWidth = lineWidth, cap = StrokeCap.Round)
                    }
                }
            }

            for (i in 0..8) {
                val visitedNode = visibleTrail && i in visited
                drawCircle(
                    color = if (visitedNode) mainColor else idleColor,
                    radius = nodeR,
                    center = nodeCenter(i, w, h),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PatternPadPreview() {
    MaterialTheme {
        PatternPad(
            title = "패턴",
            subtitle = "잠금해제 패턴을 입력해주세요.",
            visibleTrail = true,
            onEntered = {},
        )
    }
}

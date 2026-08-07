package com.eunilsung.talk.ui.uikit.watermark

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.repository.LoginRepository
import org.koin.compose.koinInject

/** 화면 전역 워터마크 — 로그인 시 "내 아이디 + 내 이름" 을 대각선으로 반복 표기. */
@Composable
fun Watermark(modifier: Modifier = Modifier) {
    if (!Config.Watermark.IS_ENABLED) return

    val loginRepository: LoginRepository = koinInject()
    val loggedIn by loginRepository.isLoggedIn.collectAsState()
    if (!loggedIn) return

    val label = listOf(Config.MyInfo.userId, Config.MyInfo.userName)
        .filter { it.isNotBlank() }
        .joinToString("\n")
    if (label.isBlank()) return

    val measurer = rememberTextMeasurer()
    val style = TextStyle(
        color = Color(0xFF888888).copy(alpha = 0.12f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val layout = measurer.measure(label, style)
        val stepX = layout.size.width + 90.dp.toPx()
        val stepY = layout.size.height + 70.dp.toPx()
        rotate(degrees = -30f) {
            var y = -size.height
            while (y < size.height * 2) {
                var x = -size.width
                while (x < size.width * 2) {
                    drawText(layout, topLeft = Offset(x, y))
                    x += stepX
                }
                y += stepY
            }
        }
    }
}

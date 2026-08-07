package com.eunilsung.talk.ui.screenlock.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.ui.theme.AppColors

private const val PIN_LENGTH = 4

/** 4자리 PIN 입력 패드 — 인디케이터(●●●●) + 숫자 키패드. */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    onEntered: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitleColor: androidx.compose.ui.graphics.Color = AppColors.TextSub,
    errorKey: Int = 0,
) {
    var input by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(errorKey) { input = "" }

    fun press(d: String) {
        if (input.length >= PIN_LENGTH) return
        input += d
        if (input.length == PIN_LENGTH) {
            val entered = input
            input = ""
            onEntered(entered)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = AppColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(subtitle, color = subtitleColor, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            repeat(PIN_LENGTH) { i ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < input.length) AppColors.Main else AppColors.TextSub.copy(alpha = 0.3f))
                )
            }
        }
        Spacer(Modifier.height(40.dp))

        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (col in 1..3) {
                    val d = (row * 3 + col).toString()
                    KeypadKey(text = d, onClick = { press(d) })
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(72.dp))
            KeypadKey(text = "0", onClick = { press("0") })
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape)
                    .clickable { if (input.isNotEmpty()) input = input.dropLast(1) },
                contentAlignment = Alignment.Center,
            ) {
                Text("⌫", color = AppColors.Text, fontSize = 26.sp)
            }
        }
    }
}

@Composable
private fun KeypadKey(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = AppColors.Text, fontSize = 28.sp)
    }
}

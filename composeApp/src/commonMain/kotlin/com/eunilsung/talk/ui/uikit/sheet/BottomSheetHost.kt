package com.eunilsung.talk.ui.uikit.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomSheetHost(content: @Composable () -> Unit) {
    var request by remember { mutableStateOf<BottomSheetRequest?>(null) }

    /**
     * 내려가는 동안 보여 줄 마지막 요청.
     *
     * 닫기로 정하는 즉시 [request] 를 비우기 때문에, 그대로 두면 **빈 시트가 미끄러져 내려가** —
     * 내려가는 게 아니라 그냥 사라지는 것처럼 보인다. 다 내려갈 때까지 내용을 붙들어 둔다.
     */
    val lastShown = remember { mutableStateOf<BottomSheetRequest?>(null) }

    val manager = remember {
        object : BottomSheetManager {
            override fun show(r: BottomSheetRequest) { request = r }
            override fun hide() { request = null }
        }
    }

    /**
     * 조립 시점에 바로 갱신한다. SideEffect 로 미루면 한 프레임 늦어, 닫히기 시작하는 그 프레임에
     * 내용이 비어 버린다 — 빈 시트가 순식간에 사라져 "내려가지 않는" 것처럼 보였다.
     */
    if (request != null) lastShown.value = request

    CompositionLocalProvider(LocalBottomSheetManager provides manager) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            BottomSheet(
                isVisible = request != null,
                onDismiss = { manager.hide() },
                bottomPadding = request?.bottomPadding ?: 0.dp,
            ) {
                when (val req = lastShown.value) {
                    is BottomSheetRequest.Custom -> req.content()
                    null -> Unit
                }
            }
        }
    }
}

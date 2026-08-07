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

    val manager = remember {
        object : BottomSheetManager {
            override fun show(r: BottomSheetRequest) { request = r }
            override fun hide() { request = null }
        }
    }

    CompositionLocalProvider(LocalBottomSheetManager provides manager) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            BottomSheet(
                isVisible = request != null,
                onDismiss = { manager.hide() },
                bottomPadding = request?.bottomPadding ?: 0.dp,
            ) {
                when (val req = request) {
                    is BottomSheetRequest.Custom -> req.content()
                    null -> Unit
                }
            }
        }
    }
}

package com.eunilsung.talk.ui.uikit.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

interface BottomSheetManager {
    fun show(request: BottomSheetRequest)
    fun hide()

    fun custom(bottomPadding: Dp = 0.dp, content: @Composable () -> Unit) {
        show(BottomSheetRequest.Custom(content, bottomPadding))
    }
}

sealed interface BottomSheetRequest {
    val bottomPadding: Dp

    data class Custom(
        val content: @Composable () -> Unit,
        override val bottomPadding: Dp = 0.dp,
    ) : BottomSheetRequest
}

val LocalBottomSheetManager = staticCompositionLocalOf<BottomSheetManager> {
    object : BottomSheetManager {
        override fun show(request: BottomSheetRequest) { }
        override fun hide() { }
    }
}

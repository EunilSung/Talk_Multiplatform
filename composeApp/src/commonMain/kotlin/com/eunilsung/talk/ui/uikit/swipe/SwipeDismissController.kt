package com.eunilsung.talk.ui.uikit.swipe

import androidx.compose.runtime.compositionLocalOf

class SwipeDismissController(
    val onSwipeStart: () -> Unit = {},
    val onSwipeProgress: (totalDx: Float) -> Unit = {},
    val onSwipeEnd: (velocityX: Float) -> Unit = {},
)

val LocalSwipeDismiss = compositionLocalOf<SwipeDismissController?> { null }

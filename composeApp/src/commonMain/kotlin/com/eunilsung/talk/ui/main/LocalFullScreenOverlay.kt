package com.eunilsung.talk.ui.main

import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.screen.Screen

val LocalFullScreenOverlay = staticCompositionLocalOf<(Screen?) -> Unit> {
    { }
}

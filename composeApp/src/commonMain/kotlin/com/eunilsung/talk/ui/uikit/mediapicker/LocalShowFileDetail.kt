package com.eunilsung.talk.ui.uikit.mediapicker

import androidx.compose.runtime.staticCompositionLocalOf

typealias ShowFileDetail = (
    serverFileName: String,
    originalFileName: String,
    senderLabel: String,
) -> Unit

val LocalShowFileDetail = staticCompositionLocalOf<ShowFileDetail> {
    { _, _, _ -> }
}

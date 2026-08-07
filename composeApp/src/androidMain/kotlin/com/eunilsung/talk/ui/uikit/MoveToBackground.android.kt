package com.eunilsung.talk.ui.uikit

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberMoveToBackground(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { (context as? Activity)?.moveTaskToBack(true) }
    }
}

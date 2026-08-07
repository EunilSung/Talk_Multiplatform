package com.eunilsung.talk.ui.uikit.phone

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Android — `Intent.ACTION_DIAL` 로 시스템 다이얼러 진입. */
@Composable
actual fun rememberPhoneCaller(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { number ->
            val sanitized = number.trim()
            if (sanitized.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}

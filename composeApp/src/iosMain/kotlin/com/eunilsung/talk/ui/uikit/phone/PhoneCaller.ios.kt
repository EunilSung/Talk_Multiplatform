package com.eunilsung.talk.ui.uikit.phone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** iOS — `tel:` URL 을 시스템 다이얼러로 open. */
@Composable
actual fun rememberPhoneCaller(): (String) -> Unit {
    return remember {
        { number ->
            val sanitized = number.filter { it.isTelChar() }
            if (sanitized.isNotEmpty()) {
                val url = NSURL.URLWithString("tel:$sanitized")
                if (url != null) {
                    UIApplication.sharedApplication.openURL(
                        url = url,
                        options = emptyMap<Any?, Any>(),
                        completionHandler = null,
                    )
                }
            }
        }
    }
}

/** `tel:` URI 의 valid character 만 통과. */
private fun Char.isTelChar(): Boolean =
    isDigit() || this == '+' || this == '-' || this == '.' || this == '*' || this == '#' || this == ',' || this == ';'

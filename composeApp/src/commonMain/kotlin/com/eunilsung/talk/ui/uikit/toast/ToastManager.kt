package com.eunilsung.talk.ui.uikit.toast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

interface ToastManager {
    /**
     * [message] 를 잠깐 띄운다.
     *
     * [testTag] 를 주면 문구에 그 이름이 붙어 화면 자동화(Maestro)가 기기 언어와 상관없이 이 토스트를 찾는다.
     * 흐름이 확인하는 토스트만 `TestTags` 의 이름을 준다.
     */
    fun show(message: String, icon: (@Composable () -> Unit)? = null, testTag: String? = null)
}

val LocalToastManager = staticCompositionLocalOf<ToastManager> {
    object : ToastManager {
        override fun show(message: String, icon: (@Composable () -> Unit)?, testTag: String?) { }
    }
}

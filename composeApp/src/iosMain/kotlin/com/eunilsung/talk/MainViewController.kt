package com.eunilsung.talk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import cafe.adriel.voyager.navigator.Navigator
import com.eunilsung.talk.di.appModule
import com.eunilsung.talk.ui.main.MainScreen
import org.koin.core.context.startKoin
import org.koin.core.error.KoinApplicationAlreadyStartedException

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController() = ComposeUIViewController(
    configure = {
        onFocusBehavior = OnFocusBehavior.DoNothing
        parallelRendering = false
        registerIosForegroundRedraw()
        registerAppSwitcherCover()

        try {
            startKoin {
                modules(appModule, platformModule)
            }
        } catch (_: KoinApplicationAlreadyStartedException) {
        }
    }
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                foregroundRedrawTick.value
                drawContent()
            }
    ) {
        Navigator(MainScreen)
    }
}

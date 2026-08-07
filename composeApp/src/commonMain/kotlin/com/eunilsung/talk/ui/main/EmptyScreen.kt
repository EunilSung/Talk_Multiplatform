package com.eunilsung.talk.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.logo_icon
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource

object EmptyScreen : Screen {
    @Composable
    override fun Content() {
        Box(Modifier.fillMaxSize().background(AppColors.Bg), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(Res.drawable.logo_icon),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = AppColors.LightGray
            )
        }
    }
}
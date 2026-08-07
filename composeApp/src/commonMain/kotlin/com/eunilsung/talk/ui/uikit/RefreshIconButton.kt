package com.eunilsung.talk.ui.uikit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.refresh_icon
import kotlinx.coroutines.isActive
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun RefreshIconButton(
    isRefreshing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground,
) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            angle.snapTo(0f)
            while (isActive) {
                angle.animateTo(angle.value + 360f, tween(durationMillis = 700, easing = LinearEasing))
            }
        } else {
            angle.snapTo(0f)
        }
    }

    IconButton(
        modifier = modifier.size(28.dp),
        onClick = onClick,
        enabled = !isRefreshing,
    ) {
        Icon(
            painter = painterResource(Res.drawable.refresh_icon),
            contentDescription = "Refresh",
            modifier = Modifier.size(26.dp).rotate(angle.value),
            tint = if (isRefreshing) AppColors.PrimaryMain else tint,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RefreshIconButtonPreview() {
    MaterialTheme {
        RefreshIconButton(isRefreshing = false, onClick = {})
    }
}

package com.eunilsung.talk.ui.uikit.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.clickable

@Composable
fun SideDrawer(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    isModal: Boolean = true,
    isDrawLeft: Boolean = false,
    content: @Composable () -> Unit
) {
    if (isModal) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.Black.copy(alpha = 0.5f))
                    .clickable(
                        rippleColor = null,
                        onClick = onDismiss
                    )
            )
        }
    }

    val enterTransition = slideInHorizontally(initialOffsetX = { if (isDrawLeft) -it else it })
    val exitTransition = slideOutHorizontally(targetOffsetX = { if (isDrawLeft) -it else it })

    AnimatedVisibility(
        visible = isVisible,
        enter = enterTransition,
        exit = exitTransition,
        modifier = Modifier.fillMaxSize()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (isDrawLeft) {
                Box(modifier = Modifier.clickable(enabled = false) {}) {
                    content()
                }

                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            rippleColor = null,
                            onClick = onDismiss
                        )
                )
            } else {
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            rippleColor = null,
                            onClick = onDismiss
                        )
                )
                Box(modifier = Modifier.clickable(enabled = false) {}) {
                    content()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SideDrawerPreview() {
    MaterialTheme {
        SideDrawer(
            true,
            {},
            true,
            content = {
                Box(modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(color = MaterialTheme.colorScheme.background)
                )
            }
        )
    }
}
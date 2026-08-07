package com.eunilsung.talk.ui.uikit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberMoveToBackground(): () -> Unit = remember { {} }

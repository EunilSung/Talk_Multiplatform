package com.eunilsung.talk.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import com.eunilsung.talk.ui.uikit.sheet.LocalBottomSheetManager
import com.eunilsung.talk.util.Log

@Composable
internal fun PendingPushNavigationHandler(
    rightNavigator: cafe.adriel.voyager.navigator.Navigator,
    loginRepository: LoginRepository,
) {
    val pending by com.eunilsung.talk.data.remote.push.PendingPushNavigation.pending.collectAsState()
    val isLoggedIn by loginRepository.isLoggedIn.collectAsState()
    val isConnected by loginRepository.isConnected.collectAsState()

    val dialogManager = LocalDialogManager.current
    val bottomSheetManager = LocalBottomSheetManager.current
    val showOverlay = LocalFullScreenOverlay.current

    LaunchedEffect(pending, isLoggedIn) {
        val req = pending ?: return@LaunchedEffect

        if (!isLoggedIn) {
            Log.message("[PushNav] waiting — not logged in (key=${req.msgKey})")
            return@LaunchedEffect
        }

        if (!isConnected) {
            Log.message("[PushNav] socket disconnected — entering screen anyway, reconnect will happen on first fetch (key=${req.msgKey})")
            runCatching { loginRepository.checkConnectionAndRelogin() }
        }

        val consumed = com.eunilsung.talk.data.remote.push.PendingPushNavigation.consume()
            ?: return@LaunchedEffect
        Log.message("[PushNav] routing — kind=${consumed.kind} key=${consumed.msgKey} isConnected=$isConnected")

        dialogManager.dismiss()
        bottomSheetManager.hide()
        showOverlay(null)

        routePushNavigation(rightNavigator, consumed)
    }
}

@Composable
internal fun PendingShareNavigationHandler(
    loginRepository: LoginRepository,
) {
    val pending by com.eunilsung.talk.data.remote.share.PendingShareNavigation.pending.collectAsState()
    val isLoggedIn by loginRepository.isLoggedIn.collectAsState()
    val showOverlay = LocalFullScreenOverlay.current

    LaunchedEffect(pending, isLoggedIn) {
        val content = pending ?: return@LaunchedEffect
        if (!isLoggedIn) {
            Log.message("[ShareNav] waiting — not logged in (text=${content.text.length} files=${content.filePaths.size})")
            return@LaunchedEffect
        }
        val consumed = com.eunilsung.talk.data.remote.share.PendingShareNavigation.consume()
            ?: return@LaunchedEffect
        Log.message("[ShareNav] routing — text=${consumed.text.length} files=${consumed.filePaths.size}")
        showOverlay(com.eunilsung.talk.ui.share.ShareScreen(consumed))
    }
}

internal fun routePushNavigation(
    rightNavigator: Navigator,
    req: com.eunilsung.talk.data.remote.push.PushNavRequest,
) {
    val newScreen: Screen = when (req.kind) {
        "TALK", "CHAT" ->
            com.eunilsung.talk.ui.chatroom.ChatRoomScreen(chatRoomId = req.msgKey)
        else -> null
    } ?: return

    val current = rightNavigator.lastItem

    val alreadyOpen = when {
        current is com.eunilsung.talk.ui.chatroom.ChatRoomScreen &&
            newScreen is com.eunilsung.talk.ui.chatroom.ChatRoomScreen ->
            current.chatRoomId == newScreen.chatRoomId
        else -> false
    }
    if (alreadyOpen) {
        Log.message("[PushNav] target already open — skip re-navigation (key=${req.msgKey})")
        return
    }

    if (current is EmptyScreen) {
        rightNavigator.push(newScreen)
    } else {
        rightNavigator.replace(newScreen)
    }
}

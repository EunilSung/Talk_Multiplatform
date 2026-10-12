package com.eunilsung.talk.ui.main

import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import com.eunilsung.talk.util.Log
import com.eunilsung.talk.domain.model.PushNavRequest
import com.eunilsung.talk.ui.chatroom.ChatRoomScreen

internal fun routePushNavigation(
    rightNavigator: Navigator,
    req: PushNavRequest,
) {
    val newScreen: Screen = when (req.kind) {
        "TALK", "CHAT" ->
            ChatRoomScreen(chatRoomId = req.msgKey)
        else -> null
    } ?: return

    val current = rightNavigator.lastItem

    val alreadyOpen = when {
        current is ChatRoomScreen &&
            newScreen is ChatRoomScreen ->
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

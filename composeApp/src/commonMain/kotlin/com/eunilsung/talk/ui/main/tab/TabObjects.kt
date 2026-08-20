package com.eunilsung.talk.ui.main.tab

import androidx.compose.runtime.Composable
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_tab_icon
import multiplatformtalk.composeapp.generated.resources.chatroom_list
import multiplatformtalk.composeapp.generated.resources.group_tab_icon
import multiplatformtalk.composeapp.generated.resources.setting
import multiplatformtalk.composeapp.generated.resources.setting_tab_icon
import multiplatformtalk.composeapp.generated.resources.tab_my_group
import com.eunilsung.talk.ui.chatroomlist.ChatRoomListScreen
import com.eunilsung.talk.ui.group.GroupScreen
import com.eunilsung.talk.ui.setting.SettingScreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal object GroupTab : AppTab {
    override val key: String = "group"

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            title = stringResource(Res.string.tab_my_group),
            icon = painterResource(Res.drawable.group_tab_icon)
        )

    @Composable
    override fun Content() {
        GroupScreen.Content()
    }
}

internal object ChatTab : AppTab {
    override val key: String = "chat"

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            title = stringResource(Res.string.chatroom_list),
            icon = painterResource(Res.drawable.chat_tab_icon)
        )

    @Composable
    override fun Content() {
        ChatRoomListScreen.Content()
    }
}

internal object SettingTab : AppTab {
    override val key: String = "setting"

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            title = stringResource(Res.string.setting),
            icon = painterResource(Res.drawable.setting_tab_icon)
        )

    @Composable
    override fun Content() {
        SettingScreen.Content()
    }
}

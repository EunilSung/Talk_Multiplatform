package com.eunilsung.talk.ui.invite.tab

import androidx.compose.runtime.Composable
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.group
import multiplatformtalk.composeapp.generated.resources.group_tab_icon
import com.eunilsung.talk.ui.main.tab.AppTab
import com.eunilsung.talk.ui.main.tab.AppTabOptions
import com.eunilsung.talk.ui.invite.tab.group.InviteGroupTabContent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal object InviteGroupTab : AppTab {
    override val key: String = "invite_group"

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            title = stringResource(Res.string.group),
            icon = painterResource(Res.drawable.group_tab_icon)
        )

    @Composable
    override fun Content() {
        InviteGroupTabContent()
    }
}


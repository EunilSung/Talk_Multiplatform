package com.eunilsung.talk.ui.invite.tab

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.group
import multiplatformtalk.composeapp.generated.resources.group_tab_icon
import com.eunilsung.talk.ui.invite.tab.group.InviteGroupTabContent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal object InviteGroupTab : Tab {
    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 0u,
            title = stringResource(Res.string.group),
            icon = painterResource(Res.drawable.group_tab_icon)
        )

    @Composable
    override fun Content() {
        InviteGroupTabContent()
    }
}


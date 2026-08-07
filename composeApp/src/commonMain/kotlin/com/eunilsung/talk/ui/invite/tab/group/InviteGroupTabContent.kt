package com.eunilsung.talk.ui.invite.tab.group

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.group.GroupSearchScaffold
import com.eunilsung.talk.ui.group.item.GroupItemList
import com.eunilsung.talk.ui.invite.InviteController
import com.eunilsung.talk.ui.invite.LocalInviteController
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteGroupTabContent() {
    if (LocalInspectionMode.current) return
    val viewModel: InviteGroupViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val controller = LocalInviteController.current
    val listState = rememberLazyListState()

    GroupSearchScaffold(
        listState = listState,
        searchQuery = searchState.query,
        onSearchChange = { viewModel.onAction(InviteGroupActions.OnSearchQueryChange(it)) },
        onClearSearch = { viewModel.onAction(InviteGroupActions.OnClearSearch) },
        isShowTitle = false,
    ) { padding ->
        GroupItemList(
            groups = uiState.items,
            listState = listState,
            isToggleable = true,
            userOf = { member -> inviteUserOf(member, controller) },
            onExpand = { viewModel.onAction(InviteGroupActions.ToggleGroup(it)) },
            onUserClick = { _, member ->
                val userId = member.userId ?: ""
                if (!controller.existingUserIds.contains(userId)) {
                    controller.onUserToggle(inviteUserOf(member, controller))
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding).padding(top = 5.dp),
        )
    }
}

private fun inviteUserOf(member: Group.User, controller: InviteController): User {
    val userId = member.userId ?: ""
    val isPreSelected = controller.existingUserIds.contains(userId)
    return User(
        id = userId,
        name = member.userName ?: "",
        departmentName = member.departmentName,
        positionName = member.positionName,
        nickname = member.statusMessage,
        isSelect = isPreSelected || controller.isSelected(userId),
    )
}


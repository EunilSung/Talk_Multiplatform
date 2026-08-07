package com.eunilsung.talk.ui.group.item

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.User

@Composable
fun GroupItemList(
    groups: List<Group.Item>,
    listState: LazyListState,
    isToggleable: Boolean,
    userOf: (Group.User) -> User,
    onExpand: (String) -> Unit,
    onUserClick: (Group.Item, Group.User) -> Unit,
    onUserLongClick: ((Group.Item, Group.User) -> Unit)? = null,
    onHeaderOption: ((Group.Item) -> (() -> Unit)?)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(state = listState, modifier = modifier) {
        groups.forEach { group ->
            item(key = "header_${group.id}", contentType = "GroupHeader") {
                GroupHeaderItem(
                    item = group,
                    onExpandClick = { onExpand(group.id) },
                    onOptionClick = onHeaderOption?.invoke(group)
                )
            }

            item(key = "users_${group.id}", contentType = "GroupUserContainer") {
                GroupUserItem(
                    group = group,
                    isToggleable = isToggleable,
                    userOf = userOf,
                    onUserClick = { member -> onUserClick(group, member) },
                    onUserLongClick = onUserLongClick?.let { cb -> { member -> cb(group, member) } }
                )
            }

            item(key = "divider_${group.id}", contentType = "GroupDivider") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

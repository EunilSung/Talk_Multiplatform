package com.eunilsung.talk.ui.group.item

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.user.UserItemV1

@Composable
fun GroupUserItem(
    group: Group.Item,
    isToggleable: Boolean,
    userOf: (Group.User) -> User,
    onUserClick: (Group.User) -> Unit,
    onUserLongClick: ((Group.User) -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = group.isExpanded,
        enter = fadeIn(animationSpec = tween(200)) +
                expandVertically(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)) +
                shrinkVertically(animationSpec = tween(200))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Bg)
        ) {
            group.userData.forEach { member ->
                UserItemV1(
                    user = userOf(member),
                    isToggleable = isToggleable,
                    onClick = { onUserClick(member) },
                    onLongClick = onUserLongClick?.let { cb -> { cb(member) } }
                )
            }
        }
    }
}

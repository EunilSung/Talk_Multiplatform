package com.eunilsung.talk.ui.group.item.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.eunilsung.talk.ui.group.GroupActions
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.confirm_user_remove_from_group
import multiplatformtalk.composeapp.generated.resources.delete
import multiplatformtalk.composeapp.generated.resources.user_chat
import multiplatformtalk.composeapp.generated.resources.user_copy_to_group
import multiplatformtalk.composeapp.generated.resources.user_move_group
import multiplatformtalk.composeapp.generated.resources.user_remove_from_group
import com.eunilsung.talk.domain.model.MY_PROFILE_GROUP_ID
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.quickactions.QuickActions
import com.eunilsung.talk.ui.quickactions.rememberGroupPicker
import com.eunilsung.talk.ui.quickactions.rememberQuickActions
import com.eunilsung.talk.ui.uikit.dialog.DialogManager
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import org.jetbrains.compose.resources.stringResource

@Stable
class GroupUserItemMenu internal constructor(
    private val dialogManager: DialogManager,
    private val quickActions: QuickActions,
    private val showGroupPicker: (title: String, onPicked: (String) -> Unit) -> Unit,
    private val onAction: (GroupActions) -> Unit,
    private val labels: Labels
) {

    fun onLongClick(user: User, fromGroupId: String) {
        val isRestricted = fromGroupId == MY_PROFILE_GROUP_ID ||
                           fromGroupId.contains("-")
        if (isRestricted) showRestrictedMenu(user) else showFullMenu(user, fromGroupId)
    }

    private fun showRestrictedMenu(user: User) {
        dialogManager.list(
            title = user.name,
            items = listOf(labels.userChat, labels.copyToGroup),
            submenuItems = setOf(labels.copyToGroup),
            onSelected = { picked ->
                when (picked) {
                    labels.userChat    -> quickActions.startChat(listOf(user.id))
                    labels.copyToGroup -> showGroupPicker(labels.copyToGroup) { targetGroupId ->
                        quickActions.addUserToGroup(user.id, targetGroupId)
                    }
                }
            }
        )
    }

    private fun showFullMenu(user: User, fromGroupId: String) {
        dialogManager.list(
            title = user.name,
            items = listOf(
                labels.userChat,
                labels.copyToGroup, labels.moveGroup, labels.removeFromGroup
            ),
            submenuItems = setOf(labels.copyToGroup, labels.moveGroup),
            destructiveItems = setOf(labels.removeFromGroup),
            onSelected = { picked ->
                when (picked) {
                    labels.userChat    -> quickActions.startChat(listOf(user.id))
                    labels.copyToGroup -> showGroupPicker(labels.copyToGroup) { targetGroupId ->
                        quickActions.addUserToGroup(user.id, targetGroupId)
                    }
                    labels.moveGroup   -> showGroupPicker(labels.moveGroup) { targetGroupId ->
                        onAction(GroupActions.MoveUserToGroup(user.id, fromGroupId, targetGroupId))
                    }
                    labels.removeFromGroup -> dialogManager.confirm(
                        title = labels.removeFromGroup,
                        message = labels.confirmRemoveFromGroup,
                        confirmText = labels.delete,
                        onConfirm = {
                            onAction(GroupActions.RemoveUserFromGroup(user.id, fromGroupId))
                        }
                    )
                }
            }
        )
    }

    @Stable
    data class Labels(
        val userChat: String,
        val copyToGroup: String,
        val moveGroup: String,
        val removeFromGroup: String,
        val confirmRemoveFromGroup: String,
        val delete: String
    )
}

@Composable
fun rememberGroupUserItemMenu(
    onAction: (GroupActions) -> Unit
): GroupUserItemMenu {
    val dialogManager = LocalDialogManager.current
    val quickActions = rememberQuickActions()
    val showGroupPicker = rememberGroupPicker()
    val labels = GroupUserItemMenu.Labels(
        userChat = stringResource(Res.string.user_chat),
        copyToGroup = stringResource(Res.string.user_copy_to_group),
        moveGroup = stringResource(Res.string.user_move_group),
        removeFromGroup = stringResource(Res.string.user_remove_from_group),
        confirmRemoveFromGroup = stringResource(Res.string.confirm_user_remove_from_group),
        delete = stringResource(Res.string.delete)
    )
    return remember(dialogManager, quickActions, showGroupPicker, onAction, labels) {
        GroupUserItemMenu(dialogManager, quickActions, showGroupPicker, onAction, labels)
    }
}

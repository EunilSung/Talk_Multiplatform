package com.eunilsung.talk.ui.group.item.menu

import org.jetbrains.compose.resources.getString
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.eunilsung.talk.domain.model.isSystemGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.eunilsung.talk.ui.group.GroupActions
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.confirm_group_delete
import multiplatformtalk.composeapp.generated.resources.default_group
import multiplatformtalk.composeapp.generated.resources.delete
import multiplatformtalk.composeapp.generated.resources.group_add
import multiplatformtalk.composeapp.generated.resources.group_chat
import multiplatformtalk.composeapp.generated.resources.group_delete
import multiplatformtalk.composeapp.generated.resources.group_rename
import com.eunilsung.talk.domain.model.isMyProfileGroup
import com.eunilsung.talk.domain.model.Group
import com.eunilsung.talk.domain.model.isDefaultGroup
import com.eunilsung.talk.ui.quickactions.QuickActions
import com.eunilsung.talk.ui.quickactions.rememberQuickActions
import com.eunilsung.talk.ui.uikit.dialog.DialogManager
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import org.jetbrains.compose.resources.stringResource

@Stable
class GroupHeaderItemMenu internal constructor(
    private val dialogManager: DialogManager,
    private val quickActions: QuickActions,
    private val showCreateGroupDialog: () -> Unit,
    private val onAction: (GroupActions) -> Unit,
    private val labels: Labels,
    private val scope: CoroutineScope,
) {

    fun onLongClickHandler(group: Group.Item): (() -> Unit)? = when {
        group.isMyProfileGroup -> null
        group.isDefaultGroup || group.isSystemGroup -> ({ showSystemGroupMenu(group) })
        else -> ({ showRegularGroupMenu(group) })
    }

    private fun showSystemGroupMenu(group: Group.Item) {
        val groupUserIds = group.userData.mapNotNull { it.userId }
        dialogManager.list(
            title = displayName(group),
            items = listOf(labels.groupChat, labels.groupAdd),
            onSelected = { picked ->
                when (picked) {
                    labels.groupChat    -> quickActions.startChat(groupUserIds)
                    labels.groupAdd     -> showCreateGroupDialog()
                }
            }
        )
    }

    private fun showRegularGroupMenu(group: Group.Item) {
        val groupUserIds = group.userData.mapNotNull { it.userId }
        dialogManager.list(
            title = group.name,
            items = listOf(
                labels.groupChat, labels.groupAdd,
                labels.groupRename, labels.groupDelete
            ),
            destructiveItems = setOf(labels.groupDelete),
            onSelected = { picked ->
                when (picked) {
                    labels.groupChat    -> quickActions.startChat(groupUserIds)
                    labels.groupAdd     -> showCreateGroupDialog()
                    labels.groupRename  -> dialogManager.textInput(
                        title = labels.groupRename,
                        initialValue = group.name,
                        hint = group.name,
                        onConfirm = { rawInput ->
                            onAction(GroupActions.RenameGroup(groupId = group.id, newName = rawInput))
                        }
                    )
                    labels.groupDelete  -> scope.launch {
                        dialogManager.confirm(
                            title = labels.groupDelete,
                            message = getString(Res.string.confirm_group_delete, group.name),
                            confirmText = labels.delete,
                            onConfirm = { onAction(GroupActions.DeleteGroup(group.id)) }
                        )
                    }
                }
            }
        )
    }

    private fun displayName(group: Group.Item): String =
        if (group.isDefaultGroup) labels.defaultGroup else group.name

    @Stable
    data class Labels(
        val groupChat: String,
        val groupAdd: String,
        val groupRename: String,
        val groupDelete: String,
        val defaultGroup: String,
        val delete: String
    )
}

@Composable
fun rememberGroupItemMenu(
    showCreateGroupDialog: () -> Unit,
    onAction: (GroupActions) -> Unit
): GroupHeaderItemMenu {
    val dialogManager = LocalDialogManager.current
    val quickActions = rememberQuickActions()
    val labels = GroupHeaderItemMenu.Labels(
        groupChat = stringResource(Res.string.group_chat),
        groupAdd = stringResource(Res.string.group_add),
        groupRename = stringResource(Res.string.group_rename),
        groupDelete = stringResource(Res.string.group_delete),
        defaultGroup = stringResource(Res.string.default_group),
        delete = stringResource(Res.string.delete)
    )
    val scope = rememberCoroutineScope()
    return remember(dialogManager, quickActions, showCreateGroupDialog, onAction, labels, scope) {
        GroupHeaderItemMenu(dialogManager, quickActions, showCreateGroupDialog, onAction, labels, scope)
    }
}

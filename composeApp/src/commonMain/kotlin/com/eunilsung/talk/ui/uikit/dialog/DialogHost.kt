package com.eunilsung.talk.ui.uikit.dialog

import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.ok
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_group_delete_confirm
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.stringResource

@Composable
fun DialogHost(content: @Composable () -> Unit) {
    var request by remember { mutableStateOf<DialogRequest?>(null) }

    val manager = remember {
        object : DialogManager {
            override fun show(r: DialogRequest) {
                request = r
            }
            override fun dismiss() {
                request = null
            }
        }
    }

    CompositionLocalProvider(LocalDialogManager provides manager) {
        content()
        when (val req = request) {
            is DialogRequest.Alert -> Button1Dialog(
                onDismissRequest = {
                    request = null
                    req.onConfirm()
                },
                description = req.message
            )
            is DialogRequest.Confirm -> Button2Dialog(
                onDismissRequest = {
                    request = null
                    req.onCancel()
                },
                description = {
                    Text(
                        text = req.message,
                        color = AppColors.TextSub,
                        fontSize = 14.sp
                    )
                              },
                confirmText = req.confirmText ?: stringResource(Res.string.ok),
                dismissText = req.dismissText ?: stringResource(Res.string.cancel),
                onConfirm = {
                    request = null
                    req.onConfirm()
                },
                onDismiss = {
                    request = null
                    req.onCancel()
                }
            )
            is DialogRequest.ListSelect -> ListDialog(
                titleTxt = req.title,
                list = req.items,
                destructiveItems = req.destructiveItems,
                submenuItems = req.submenuItems,
                onDismiss = {
                    request = null
                    req.onCancel()
                },
                onOptionSelected = { picked ->
                    request = null
                    req.onSelected(picked)
                }
            )
            is DialogRequest.TextInput -> TextFieldButton2Dialog(
                onDismissRequest = {
                    request = null
                    req.onCancel()
                },
                title = { Text(req.title) },
                initialValue = req.initialValue,
                hintTxt = req.hint,
                confirmText = req.confirmText ?: stringResource(Res.string.ok),
                dismissText = req.dismissText ?: stringResource(Res.string.cancel),
                onConfirm = { input ->
                    request = null
                    req.onConfirm(input)
                },
                onDismiss = {
                    request = null
                    req.onCancel()
                }
            )
            null -> Unit
        }
    }
}

package com.eunilsung.talk.ui.chatroomlist.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_left_icon
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.chat_group_delete_button
import multiplatformtalk.composeapp.generated.resources.chat_group_delete_confirm
import multiplatformtalk.composeapp.generated.resources.chat_group_delete_title
import multiplatformtalk.composeapp.generated.resources.chat_group_edit_title
import multiplatformtalk.composeapp.generated.resources.chat_group_name_label
import multiplatformtalk.composeapp.generated.resources.chat_group_name_placeholder
import multiplatformtalk.composeapp.generated.resources.delete
import multiplatformtalk.composeapp.generated.resources.done
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.BackHandler
import com.eunilsung.talk.ui.uikit.button.ButtonV1
import com.eunilsung.talk.ui.uikit.dialog.Button2Dialog
import com.eunilsung.talk.ui.uikit.textfield.TextFieldV1
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val MAX_GROUP_NAME_LEN = 10

/** 그룹 편집 화면 — 이름 변경 및 그룹 삭제. */
class ChatRoomGroupEditScreen(
    private val groupId: String,
    private val groupName: String,
) : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: ChatRoomRoomGroupManageViewModel = koinViewModel()
        val showOverlay = LocalFullScreenOverlay.current
        var name by remember { mutableStateOf(groupName) }

        BackHandler { showOverlay(ChatRoomGroupManageScreen) }

        ChatRoomGroupEditContent(
            name = name,
            onNameChange = { name = it },
            onBack = { showOverlay(ChatRoomGroupManageScreen) },
            onDone = {
                viewModel.rename(groupId, name.trim())
                showOverlay(ChatRoomGroupManageScreen)
            },
            onDelete = {
                viewModel.delete(groupId)
                showOverlay(ChatRoomGroupManageScreen)
            }
        )
    }
}

/** 상태 없는 UI — 상태/이벤트는 콜백으로 위임. */
@Composable
fun ChatRoomGroupEditContent(
    name: String,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBarV1(
                title = stringResource(Res.string.chat_group_edit_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_left_icon),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = onDone,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = AppColors.PrimaryMain,
                            disabledContentColor = AppColors.TextDisabled
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.done),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppColors.Bg)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.chat_group_name_label),
                        color = AppColors.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${name.length}/$MAX_GROUP_NAME_LEN",
                        color = AppColors.TextSub,
                        fontSize = 12.sp
                    )
                }

                TextFieldV1(
                    inputText = name,
                    hintText = stringResource(Res.string.chat_group_name_placeholder),
                    singleLine = true,
                    onValueChange = { if (it.length <= MAX_GROUP_NAME_LEN) onNameChange(it) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            ButtonV1(
                text = stringResource(Res.string.chat_group_delete_button),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(50.dp),
                round = 12.dp,
                containerColor = AppColors.Out,
                contentColor = AppColors.White,
                onClick = { showDeleteConfirm = true }
            )
        }
    }

    if (showDeleteConfirm) {
        Button2Dialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = stringResource(Res.string.chat_group_delete_title),
                    color = AppColors.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            description = {
                Text(
                    text = stringResource(Res.string.chat_group_delete_confirm),
                    color = AppColors.TextSub,
                    fontSize = 14.sp
                )
            },
            confirmText = stringResource(Res.string.delete),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = { onDelete() }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatRoomGroupEditContentPreview() {
    MaterialTheme {
        ChatRoomGroupEditContent(
            name = "연구실",
            onNameChange = {},
            onBack = {},
            onDone = {},
            onDelete = {}
        )
    }
}

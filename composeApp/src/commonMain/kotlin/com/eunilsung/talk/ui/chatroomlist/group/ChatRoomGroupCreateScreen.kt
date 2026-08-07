package com.eunilsung.talk.ui.chatroomlist.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import multiplatformtalk.composeapp.generated.resources.chat_group_create
import multiplatformtalk.composeapp.generated.resources.chat_group_name_label
import multiplatformtalk.composeapp.generated.resources.chat_group_name_placeholder
import multiplatformtalk.composeapp.generated.resources.ok
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.BackHandler
import com.eunilsung.talk.ui.uikit.textfield.TextFieldV1
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val MAX_GROUP_NAME_LEN = 10

/** 그룹 만들기 화면 — [ChatRoomGroupManageScreen] 의 "그룹 만들기" 에서 이동. 뒤로/확인 시 관리 화면으로 복귀. */
object ChatRoomGroupCreateScreen : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: ChatRoomRoomGroupManageViewModel = koinViewModel()
        val showOverlay = LocalFullScreenOverlay.current
        var name by remember { mutableStateOf("") }

        BackHandler { showOverlay(ChatRoomGroupManageScreen) }

        ChatRoomGroupCreateContent(
            name = name,
            onNameChange = { name = it },
            onBack = { showOverlay(ChatRoomGroupManageScreen) },
            onConfirm = {
                viewModel.create(name.trim())
                showOverlay(ChatRoomGroupManageScreen)
            }
        )
    }
}

/** 상태 없는 UI — 프리뷰/테스트 가능. 상태(name)와 뒤로/확인은 콜백으로 위임. */
@Composable
fun ChatRoomGroupCreateContent(
    name: String,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopBarV1(
                title = stringResource(Res.string.chat_group_create),
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
                        onClick = onConfirm,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = AppColors.PrimaryMain,
                            disabledContentColor = AppColors.TextDisabled
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.ok),
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
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
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
    }
}

@Preview(showBackground = true)
@Composable
fun ChatRoomGroupCreateContentPreview() {
    MaterialTheme {
        ChatRoomGroupCreateContent(
            name = "연구실",
            onNameChange = {},
            onBack = {},
            onConfirm = {}
        )
    }
}

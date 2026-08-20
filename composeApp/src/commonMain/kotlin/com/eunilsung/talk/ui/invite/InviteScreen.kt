package com.eunilsung.talk.ui.invite

import multiplatformtalk.composeapp.generated.resources.toast_invite_failed
import multiplatformtalk.composeapp.generated.resources.invite_to_chat_room
import multiplatformtalk.composeapp.generated.resources.invite_new_chat_room
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.confirm_count
import multiplatformtalk.composeapp.generated.resources.close_icon
import multiplatformtalk.composeapp.generated.resources.group
import com.eunilsung.talk.ui.main.tab.CurrentAppTab
import com.eunilsung.talk.ui.main.tab.rememberAppTabState
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.chatroom.ChatRoomScreen
import com.eunilsung.talk.ui.invite.item.SelectedUserChips
import com.eunilsung.talk.ui.invite.tab.InviteGroupTab
import com.eunilsung.talk.ui.main.EmptyScreen
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.main.LocalRightNavigator
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.line.LineDivider
import com.eunilsung.talk.ui.uikit.tab.TabItem1
import com.eunilsung.talk.ui.uikit.tab.TabRow1
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class InviteScreen(
    val mode: InviteMode,
) : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: InviteViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val showOverlay = LocalFullScreenOverlay.current
        val rightNavigator = LocalRightNavigator.current
        val toastManager = LocalToastManager.current
        val txtInviteFailed = stringResource(Res.string.toast_invite_failed)

        LaunchedEffect(mode) {
            viewModel.onAction(InviteActions.Init(mode))
        }

        LaunchedEffect(viewModel) {
            viewModel.submitResult.collect { result ->
                when (result) {
                    is InviteViewModel.SubmitResult.Success -> {
                        showOverlay(null)
                        val createdNewRoom = mode is InviteMode.CreateChatRoom ||
                            (mode is InviteMode.InviteToChatRoom && mode.createNewRoom)
                        if (createdNewRoom && result.chatRoomId.isNotBlank()) {
                            val newScreen = ChatRoomScreen(chatRoomId = result.chatRoomId)
                            if (rightNavigator.lastItem is EmptyScreen) {
                                rightNavigator.push(newScreen)
                            } else {
                                rightNavigator.replace(newScreen)
                            }
                        }
                    }
                    is InviteViewModel.SubmitResult.Failure ->
                        toastManager.show(txtInviteFailed)
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose { viewModel.onAction(InviteActions.Reset) }
        }

        InviteScreenContent(
            uiState = uiState,
            mode = mode,
            onAction = { action ->
                when (action) {
                    is InviteActions.OnClose -> showOverlay(null)
                    else -> viewModel.onAction(action)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreenContent(
    uiState: InviteUiState,
    mode: InviteMode,
    onAction: (InviteActions) -> Unit = {},
) {
    val controller = remember(uiState.selectedUsers, uiState.existingUserIds) {
        InviteController(
            selectedUsers = uiState.selectedUsers,
            existingUserIds = uiState.existingUserIds,
            onUserToggle = { user -> onAction(InviteActions.OnUserToggle(user)) }
        )
    }

    CompositionLocalProvider(LocalInviteController provides controller) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBarV1(
                title = titleForMode(mode),
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.size(28.dp),
                        onClick = { onAction(InviteActions.OnClose) }) {
                        Icon(
                            painter = painterResource(Res.drawable.close_icon),
                            modifier = Modifier.size(28.dp),
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    val enabled = uiState.selectedUsers.isNotEmpty() && !uiState.isSubmitting
                    TextButton(
                        enabled = enabled,
                        onClick = { onAction(InviteActions.OnSubmit) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = AppColors.Main,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = stringResource(Res.string.confirm_count, uiState.selectedUsers.size),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            )

            val tabState = rememberAppTabState(listOf(InviteGroupTab), InviteGroupTab)
            val groupTitle = stringResource(Res.string.group)
            TabRow1(
                items = listOf(
                    TabItem1(
                        text = groupTitle,
                        isSelected = tabState.current == InviteGroupTab,
                        onClick = { tabState.current = InviteGroupTab },
                    ),
                )
            )

            Spacer(modifier = Modifier.size(10.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                CurrentAppTab(tabState)
            }

            if (uiState.selectedUsers.isNotEmpty()) {
                LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
                SelectedUserChips(
                    selectedUsers = uiState.selectedUsers,
                    onRemove = { id -> onAction(InviteActions.OnUserRemove(id)) }
                )
            }
        }
    }
}

@Composable
private fun titleForMode(mode: InviteMode): String = when (mode) {
    is InviteMode.InviteToChatRoom -> stringResource(Res.string.invite_to_chat_room)
    is InviteMode.CreateChatRoom -> stringResource(Res.string.invite_new_chat_room)
}

@Preview(showBackground = true)
@Composable
fun InviteScreenPreview() {
    MaterialTheme {
        InviteScreenContent(
            uiState = InviteUiState(
                selectedUsers = listOf(
                    User(id = "u1", name = "EunilSung"),
                    User(id = "u2", name = "홍길동"),
                    User(id = "u3", name = "김철수"),
                ),
            ),
            mode = InviteMode.InviteToChatRoom(chatRoomId = "abc"),
        )
    }
}


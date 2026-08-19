package com.eunilsung.talk.ui.share

import multiplatformtalk.composeapp.generated.resources.toast_send_failed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chatroom_list
import multiplatformtalk.composeapp.generated.resources.close_icon
import multiplatformtalk.composeapp.generated.resources.group
import multiplatformtalk.composeapp.generated.resources.send
import multiplatformtalk.composeapp.generated.resources.share
import kotlinx.coroutines.flow.MutableStateFlow
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.data.remote.share.SharedContent
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.ui.chatroom.ChatRoomScreen
import com.eunilsung.talk.ui.invite.InviteController
import com.eunilsung.talk.ui.invite.LocalInviteController
import com.eunilsung.talk.ui.invite.tab.InviteGroupTab
import com.eunilsung.talk.ui.main.EmptyScreen
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.share.item.SelectedChatRoomChip
import com.eunilsung.talk.ui.share.item.SelectedUserChips
import com.eunilsung.talk.ui.share.tab.LocalShareChatRoomController
import com.eunilsung.talk.ui.share.tab.ShareChatRoomController
import com.eunilsung.talk.ui.share.tab.ShareChatRoomTab
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.line.LineDivider
import com.eunilsung.talk.ui.uikit.tab.TabItem1
import com.eunilsung.talk.ui.uikit.tab.TabRow1
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class ShareScreen(
    private val content: SharedContent,
) : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: ShareViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val rooms by viewModel.chatRooms.collectAsState()
        val showOverlay = LocalFullScreenOverlay.current
        val rightNavigator = com.eunilsung.talk.ui.main.LocalRightNavigator.current
        val toastManager = LocalToastManager.current
        val txtSendFailed = stringResource(Res.string.toast_send_failed)

        LaunchedEffect(content) {
            viewModel.onAction(ShareActions.Init(content))
        }

        LaunchedEffect(viewModel) {
            viewModel.submitResult.collect { result ->
                when (result) {
                    is ShareViewModel.SubmitResult.NavigateToRoom -> {
                        showOverlay(null)
                        val current = rightNavigator.lastItem
                        val alreadyOpen = current is ChatRoomScreen &&
                            current.chatRoomId == result.chatRoomId
                        if (!alreadyOpen) {
                            val newScreen = ChatRoomScreen(
                                chatRoomId = result.chatRoomId
                            )
                            if (current is EmptyScreen) {
                                rightNavigator.push(newScreen)
                            } else {
                                rightNavigator.replace(newScreen)
                            }
                        }
                    }
                    is ShareViewModel.SubmitResult.Failure ->
                        toastManager.show(txtSendFailed)
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose { viewModel.onAction(ShareActions.Reset) }
        }

        ShareScreenContent(
            uiState = uiState,
            rooms = rooms,
            onAction = { action ->
                when (action) {
                    is ShareActions.OnClose -> showOverlay(null)
                    else -> viewModel.onAction(action)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreenContent(
    uiState: ShareUiState,
    rooms: List<ChatRoom.Item>,
    onAction: (ShareActions) -> Unit = {},
) {
    val inviteController = remember(uiState.selectedUsers) {
        InviteController(
            selectedUsers = uiState.selectedUsers,
            existingUserIds = emptySet(),
            onUserToggle = { user -> onAction(ShareActions.OnUserToggle(user)) }
        )
    }

    val roomsFlow = remember(rooms) {
        MutableStateFlow(rooms)
    }
    val chatRoomController = remember(roomsFlow, uiState.selectedChatRoom?.id) {
        ShareChatRoomController(
            rooms = roomsFlow,
            selectedRoomId = uiState.selectedChatRoom?.id,
            onSelect = { room -> onAction(ShareActions.OnChatRoomSelect(room)) }
        )
    }

    CompositionLocalProvider(
        LocalInviteController provides inviteController,
        LocalShareChatRoomController provides chatRoomController,
    ) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBarV1(
                title = stringResource(Res.string.share),
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.size(28.dp),
                        onClick = { onAction(ShareActions.OnClose) }) {
                        Icon(
                            painter = painterResource(Res.drawable.close_icon),
                            modifier = Modifier.size(28.dp),
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    val enabled = uiState.canSubmit
                    val label = when {
                        uiState.selectedUsers.isNotEmpty() -> "${stringResource(Res.string.send)} (${uiState.selectedUsers.size})"
                        uiState.selectedChatRoom != null -> stringResource(Res.string.send)
                        else -> stringResource(Res.string.send)
                    }
                    TextButton(
                        enabled = enabled,
                        onClick = { onAction(ShareActions.OnSubmit) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = AppColors.Main,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            )

            TabNavigator(InviteGroupTab) { tabNavigator ->
                val groupTitle = stringResource(Res.string.group)
                val chatRoomTitle = stringResource(Res.string.chatroom_list)
                TabRow1(
                    items = listOf(
                        TabItem1(
                            text = groupTitle,
                            isSelected = tabNavigator.current == InviteGroupTab,
                            onClick = { tabNavigator.current = InviteGroupTab },
                        ),
                        TabItem1(
                            text = chatRoomTitle,
                            isSelected = tabNavigator.current == ShareChatRoomTab,
                            onClick = { tabNavigator.current = ShareChatRoomTab },
                        ),
                    )
                )

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    CurrentTab()
                }

                if (uiState.selectedUsers.isNotEmpty()) {
                    LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
                    SelectedUserChips(
                        selectedUsers = uiState.selectedUsers,
                        onRemove = { id -> onAction(ShareActions.OnUserRemove(id)) }
                    )
                } else if (uiState.selectedChatRoom != null) {
                    LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
                    SelectedChatRoomChip(
                        room = uiState.selectedChatRoom,
                        onRemove = { onAction(ShareActions.OnChatRoomClear) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ShareScreenPreview() {
    MaterialTheme {
        ShareScreenContent(
            uiState = ShareUiState(
                selectedUsers = listOf(
                    User(id = "u1", name = "EunilSung"),
                    User(id = "u2", name = "홍길동"),
                    User(id = "u3", name = "김철수"),
                ),
            ),
            rooms = emptyList(),
        )
    }
}

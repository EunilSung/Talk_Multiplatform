package com.eunilsung.talk.ui.chatroom.vote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_left_icon
import com.eunilsung.talk.domain.model.VoteDetail
import com.eunilsung.talk.domain.model.VoteOption
import com.eunilsung.talk.domain.model.VoteResult
import com.eunilsung.talk.domain.model.VoteResultItem
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.BackHandler
import com.eunilsung.talk.ui.uikit.RefreshIconButton
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import com.eunilsung.talk.ui.chatroom.vote.component.VoteCreateContent
import com.eunilsung.talk.ui.chatroom.vote.component.VoteListContent
import com.eunilsung.talk.ui.chatroom.vote.component.VoteParticipateContent
import com.eunilsung.talk.ui.chatroom.vote.component.VoteResultContent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import multiplatformtalk.composeapp.generated.resources.vote
import multiplatformtalk.composeapp.generated.resources.vote_create
import multiplatformtalk.composeapp.generated.resources.vote_create_action
import multiplatformtalk.composeapp.generated.resources.vote_do
import multiplatformtalk.composeapp.generated.resources.vote_result
import org.koin.compose.viewmodel.koinViewModel

class VoteScreen(
    val chatRoomId: String = "",
    val initialMode: VoteMode = VoteMode.LIST,
    val voteId: String? = null,
) : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: VoteViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val showOverlay = LocalFullScreenOverlay.current

        LaunchedEffect(initialMode, voteId) {
            viewModel.onAction(VoteActions.Load(initialMode, voteId, chatRoomId))
        }

        LaunchedEffect(Unit) {
            viewModel.closeScreen.collect { showOverlay(null) }
        }

        VoteContent(
            uiState = uiState,
            onAction = viewModel::onAction,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteContent(
    uiState: VoteUiState,
    onAction: (VoteActions) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val handleBack: () -> Unit = { onAction(VoteActions.OnBack) }
    BackHandler(enabled = true, onBack = handleBack)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopBarV1(
                title = titleFor(uiState.mode),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_left_icon),
                            modifier = Modifier.size(28.dp),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    if (uiState is VoteUiState.List) {
                        RefreshIconButton(
                            isRefreshing = uiState.isRefreshing,
                            onClick = { onAction(VoteActions.OnRefresh) },
                        )
                        TextButton(onClick = { onAction(VoteActions.OnCreateClick) }) {
                            Text(stringResource(Res.string.vote_create_action), color = AppColors.Main, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Bg)
                .padding(padding),
        ) {
            when (uiState) {
                is VoteUiState.Loading ->
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AppColors.Main,
                    )
                is VoteUiState.List ->
                    VoteListContent(state = uiState, onAction = onAction)
                is VoteUiState.Create ->
                    VoteCreateContent(state = uiState, onAction = onAction)
                is VoteUiState.Participate ->
                    VoteParticipateContent(state = uiState, onAction = onAction)
                is VoteUiState.Result ->
                    VoteResultContent(state = uiState, onAction = onAction)
            }
        }
    }
}

@Composable
private fun titleFor(mode: VoteMode): String = when (mode) {
    VoteMode.LIST -> stringResource(Res.string.vote)
    VoteMode.CREATE -> stringResource(Res.string.vote_create)
    VoteMode.PARTICIPATE -> stringResource(Res.string.vote_do)
    VoteMode.RESULT -> stringResource(Res.string.vote_result)
}

@Preview
@Composable
private fun VoteContentListPreview() {
    MaterialTheme {
        VoteContent(uiState = VoteUiState.List())
    }
}

@Preview
@Composable
private fun VoteContentResultPreview() {
    MaterialTheme {
        VoteContent(
            uiState = VoteUiState.Result(
                VoteResult(
                    title = "점심 메뉴",
                    totalVotes = 15,
                    items = listOf(
                        VoteResultItem(idx = 0, content = "한식", nVote = 5),
                        VoteResultItem(idx = 1, content = "중식", nVote = 2),
                        VoteResultItem(idx = 2, content = "일식", nVote = 7),
                        VoteResultItem(idx = 3, content = "분식", nVote = 1),
                    ),
                ),
            ),
        )
    }
}

@Preview
@Composable
private fun VoteContentParticipatePreview() {
    MaterialTheme {
        VoteContent(
            uiState = VoteUiState.Participate(
                detail = VoteDetail(
                    title = "회식 날짜",
                    options = listOf(
                        VoteOption(idx = 0, content = "금요일"),
                        VoteOption(idx = 1, content = "다음주 화요일"),
                    ),
                ),
                selected = setOf(0),
            ),
        )
    }
}

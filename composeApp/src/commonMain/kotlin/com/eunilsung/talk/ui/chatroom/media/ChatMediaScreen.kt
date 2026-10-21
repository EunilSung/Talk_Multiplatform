package com.eunilsung.talk.ui.chatroom.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.chatroom.ChatImageGallery
import com.eunilsung.talk.ui.chatroom.item.fileIconFor
import com.eunilsung.talk.ui.chatroom.toDetailPhoto
import com.eunilsung.talk.ui.main.FileDetailSheetBinding
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.TestTags
import com.eunilsung.talk.ui.uikit.click.itemClickable
import com.eunilsung.talk.ui.uikit.image.UrlImage
import com.eunilsung.talk.ui.uikit.mediapicker.LocalShowFileDetail
import com.eunilsung.talk.ui.uikit.mediapicker.PhotoDetailDialog
import com.eunilsung.talk.ui.uikit.sheet.BottomSheetHost
import com.eunilsung.talk.ui.uikit.tab.TabItem1
import com.eunilsung.talk.ui.uikit.tab.TabRow1
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import com.eunilsung.talk.ui.util.chatDateHeaderText
import com.eunilsung.talk.ui.util.formatChatTime
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_left_icon
import multiplatformtalk.composeapp.generated.resources.chat_media
import multiplatformtalk.composeapp.generated.resources.chat_media_empty_file
import multiplatformtalk.composeapp.generated.resources.chat_media_empty_photo_video
import multiplatformtalk.composeapp.generated.resources.chat_media_tab_file
import multiplatformtalk.composeapp.generated.resources.chat_media_tab_photo_video
import multiplatformtalk.composeapp.generated.resources.date_ampm_am
import multiplatformtalk.composeapp.generated.resources.date_ampm_pm
import multiplatformtalk.composeapp.generated.resources.toast_download_done
import multiplatformtalk.composeapp.generated.resources.toast_download_failed
import multiplatformtalk.composeapp.generated.resources.toast_file_open_failed
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 대화방 모아보기 — 방의 사진·동영상 / 파일을 탭으로 나눠 전체 화면으로 보인다(대화방 서랍 "모아보기").
 *
 * 이 기기에 받아 둔 대화만 보인다. 항목을 누르면 대화방에서 같은 대화를 눌렀을 때와 똑같이 연다 —
 * 사진·동영상은 상세보기(방의 사진·동영상을 좌우로 넘김), 파일은 파일 상세 시트.
 * 전체 화면 오버레이는 앱의 시트 연결 바깥에 그려지므로, 이 화면이 시트와 파일 상세 연결을 직접 감싼다.
 */
class ChatMediaScreen(
    val chatRoomId: String,
) : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: ChatMediaViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val showOverlay = LocalFullScreenOverlay.current
        val toast = LocalToastManager.current

        LaunchedEffect(chatRoomId) {
            viewModel.onAction(ChatMediaActions.Load(chatRoomId))
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ChatMediaEvent.FileDownloaded ->
                        toast.show(getString(Res.string.toast_download_done, event.displayName))
                    ChatMediaEvent.FileDownloadFailed -> toast.show(getString(Res.string.toast_download_failed))
                    ChatMediaEvent.FileOpenFailed -> toast.show(getString(Res.string.toast_file_open_failed))
                }
            }
        }

        BottomSheetHost {
            FileDetailSheetBinding(
                onDownload = { source, name -> viewModel.onAction(ChatMediaActions.OnDownloadFile(source, name)) },
                onOpen = { source, name -> viewModel.onAction(ChatMediaActions.OnOpenFile(source, name)) },
            ) {
                ChatMediaContent(
                    uiState = uiState,
                    onAction = viewModel::onAction,
                    onBack = { showOverlay(null) },
                )
            }
        }
    }
}

@Composable
fun ChatMediaContent(
    uiState: ChatMediaUiState,
    onAction: (ChatMediaActions) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val showFileDetail = LocalShowFileDetail.current
    val amLabel = stringResource(Res.string.date_ampm_am)
    val pmLabel = stringResource(Res.string.date_ampm_pm)
    var gallery by remember { mutableStateOf<ChatImageGallery?>(null) }

    /** 대화방 파일 클릭과 같다 — 파일 상세 시트에 보낸 사람과 보낸 시각을 보인다. */
    val openFile: (Chat.Item) -> Unit = { item ->
        val sender = listOf(item.user.name, formatChatTime(item.date, amLabel, pmLabel))
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        showFileDetail(item.imagePath, item.originalFileName, sender)
    }

    /** 대화방 사진·동영상 클릭과 같다 — 방의 사진·동영상을 좌우로 넘겨 본다. */
    val openMedia: (Chat.Item) -> Unit = { item ->
        val index = uiState.galleryMedia.indexOfFirst { it.chatID == item.chatID }
        gallery = if (index >= 0) {
            ChatImageGallery(uiState.galleryMedia.map { it.toDetailPhoto() }, index)
        } else {
            ChatImageGallery(listOf(item.toDetailPhoto()), 0)
        }
    }

    Scaffold(
        containerColor = AppColors.Bg,
        topBar = {
            TopBarV1(
                title = stringResource(Res.string.chat_media),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TestTags.CHAT_MEDIA_BACK)) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_left_icon),
                            modifier = Modifier.size(28.dp),
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                bottomContent = {
                    TabRow1(
                        items = listOf(
                            TabItem1(
                                text = stringResource(Res.string.chat_media_tab_photo_video),
                                isSelected = uiState.tab == ChatMediaTab.PHOTO_VIDEO,
                                onClick = { onAction(ChatMediaActions.OnTabChange(ChatMediaTab.PHOTO_VIDEO)) },
                                testTag = TestTags.chatMediaTab(ChatMediaTab.PHOTO_VIDEO.name),
                            ),
                            TabItem1(
                                text = stringResource(Res.string.chat_media_tab_file),
                                isSelected = uiState.tab == ChatMediaTab.FILE,
                                onClick = { onAction(ChatMediaActions.OnTabChange(ChatMediaTab.FILE)) },
                                testTag = TestTags.chatMediaTab(ChatMediaTab.FILE.name),
                            ),
                        )
                    )
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = AppColors.Main,
                )
                uiState.tab == ChatMediaTab.PHOTO_VIDEO -> PhotoVideoGrid(
                    items = uiState.photosAndVideos,
                    onClick = openMedia,
                )
                else -> FileList(
                    items = uiState.files,
                    onFileClick = openFile,
                )
            }
        }
    }

    gallery?.let { opened ->
        PhotoDetailDialog(
            photos = opened.photos,
            initialIndex = opened.startIndex,
            onDismiss = { gallery = null },
        )
    }
}

/** 보낸 날짜마다 묶는다. 순서는 그대로 둔다. */
private fun groupByDay(items: List<Chat.Item>): List<Pair<String, List<Chat.Item>>> =
    items.groupBy { it.date.substringBefore(' ') }.toList()

/** 사진·동영상 그리드 — 칸 수는 화면 너비에 맞추고(칸 최소 100dp), 칸마다 테두리를 두른다. */
@Composable
private fun PhotoVideoGrid(items: List<Chat.Item>, onClick: (Chat.Item) -> Unit) {
    if (items.isEmpty()) {
        EmptyText(stringResource(Res.string.chat_media_empty_photo_video))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 2.dp,
            end = 2.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        groupByDay(items).forEach { (day, dayItems) ->
            item(key = "day_$day", span = { GridItemSpan(maxLineSpan) }) {
                DayHeader(chatDateHeaderText(dayItems.first().date))
            }
            items(dayItems, key = { it.chatID }) { item ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(AppColors.Gray100BgAuto)
                        .border(1.dp, AppColors.Line)
                        .testTag(TestTags.chatMediaItem(item.chatID)),
                ) {
                    if (item.chatType == Chat.Type.VIDEO) {
                        VideoTile(item = item, onClick = { onClick(item) })
                    } else {
                        UrlImage(
                            url = item.imagePath,
                            modifier = Modifier.fillMaxSize(),
                            onClick = { onClick(item) },
                        )
                    }
                }
            }
        }
    }
}

/** 동영상 칸 — 재생 표시와 파일 이름. */
@Composable
private fun VideoTile(item: Chat.Item, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VIDEO_TILE)
            .itemClickable(cornerRadius = 0.dp, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PlayMark(modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            text = item.originalFileName,
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlayMark(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width * 0.2f, size.height)
            close()
        }
        drawPath(path, Color.White)
        drawCircle(
            Color.White.copy(alpha = 0.25f),
            radius = size.minDimension,
            center = Offset(size.width * 0.55f, size.height / 2f),
        )
    }
}

/** 파일 목록 — 이름과 보낸 사람·날짜. */
@Composable
private fun FileList(items: List<Chat.Item>, onFileClick: (Chat.Item) -> Unit) {
    if (items.isEmpty()) {
        EmptyText(stringResource(Res.string.chat_media_empty_file))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        items(items, key = { it.chatID }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.chatMediaItem(item.chatID))
                    .itemClickable(cornerRadius = 0.dp, onClick = { onFileClick(item) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(fileIconFor(item.originalFileName)),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.originalFileName,
                        color = AppColors.Text,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = listOf(item.user.name, chatDateHeaderText(item.date))
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = AppColors.TextSub,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = AppColors.Line.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun DayHeader(day: String) {
    Text(
        text = day,
        color = AppColors.TextSub,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun EmptyText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = AppColors.TextSub, fontSize = 14.sp)
    }
}

private val VIDEO_TILE = Color(0xFF343942)

@Preview(showBackground = true)
@Composable
private fun ChatMediaContentPreview() {
    MaterialTheme {
        ChatMediaContent(
            uiState = ChatMediaUiState(chatRoomId = "room").withMedia(
                listOf(
                    Chat.Item(
                        chatID = "1", chatType = Chat.Type.VIDEO, imagePath = "a.mp4", originalFileName = "meeting.mp4",
                        date = "2026-10-16 10:00:00:000", user = User(name = "Kim"),
                    ),
                    Chat.Item(
                        chatID = "2", chatType = Chat.Type.FILE, imagePath = "b.pdf", originalFileName = "report.pdf",
                        date = "2026-10-15 10:00:00:000", user = User(name = "Lee"),
                    ),
                )
            ),
        )
    }
}

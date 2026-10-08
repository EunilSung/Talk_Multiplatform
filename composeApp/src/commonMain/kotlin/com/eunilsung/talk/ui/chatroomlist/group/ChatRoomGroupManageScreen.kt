package com.eunilsung.talk.ui.chatroomlist.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.chat_group_create
import multiplatformtalk.composeapp.generated.resources.chat_group_delete_confirm
import multiplatformtalk.composeapp.generated.resources.chat_group_delete_title
import multiplatformtalk.composeapp.generated.resources.chat_group_manage_limit
import multiplatformtalk.composeapp.generated.resources.chat_group_manage_title
import multiplatformtalk.composeapp.generated.resources.delete
import multiplatformtalk.composeapp.generated.resources.edit_name_icon
import multiplatformtalk.composeapp.generated.resources.hamburger_icon
import multiplatformtalk.composeapp.generated.resources.trashcan_icon
import multiplatformtalk.composeapp.generated.resources.unread
import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.setting.overlay.OverlayScaffold
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.button.ButtonV1
import com.eunilsung.talk.ui.uikit.dialog.Button2Dialog
import com.eunilsung.talk.ui.uikit.line.LineDivider
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/** 대화 그룹 관리 — 전체화면 오버레이. 그룹 생성/이름변경/삭제 + 드래그 순서변경(안읽음 포함). */
object ChatRoomGroupManageScreen : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel: ChatRoomRoomGroupManageViewModel = koinViewModel()
        val groups by viewModel.groups.collectAsState()
        val showOverlay = LocalFullScreenOverlay.current

        ChatRoomGroupManageContent(
            groups = groups,
            onCreate = { showOverlay(ChatRoomGroupCreateScreen) },
            onRename = { group -> showOverlay(ChatRoomGroupEditScreen(group.id, group.name)) },
            onDelete = { group -> viewModel.delete(group.id) },
            onReorder = { orderedIds -> viewModel.reorder(orderedIds) }
        )
    }
}

/** 상태 없는 UI — 프리뷰/테스트 가능. 다이얼로그 트리거는 [onCreate]/[onRename]/[onDelete] 콜백으로 위임. */
@Composable
fun ChatRoomGroupManageContent(
    groups: List<ChatGroup>,
    onCreate: () -> Unit,
    onRename: (ChatGroup) -> Unit,
    onDelete: (ChatGroup) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ChatGroup?>(null) }

    OverlayScaffold(title = stringResource(Res.string.chat_group_manage_title)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppColors.Bg)
        ) {
            Text(
                text = stringResource(Res.string.chat_group_manage_limit),
                color = AppColors.TextSub,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )

            ReorderableGroupList(
                groups = groups,
                onReorder = onReorder,
                onRename = onRename,
                onDelete = { group -> pendingDelete = group },
                modifier = Modifier.weight(1f)
            )

            LineDivider(modifier = Modifier.fillMaxWidth())

            // 안읽음(kind="1") 제외 커스텀 그룹이 최대치(10개)면 생성 버튼 비활성화.
            val maxReached = groups.count { it.kind != "1" } >= 10
            ButtonV1(
                text = stringResource(Res.string.chat_group_create),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(50.dp),
                round = 12.dp,
                containerColor = if (maxReached) AppColors.Line else AppColors.PrimaryMain,
                contentColor = if (maxReached) AppColors.TextDisabled else AppColors.White,
                onClick = { if (!maxReached) onCreate() }
            )
        }
    }

    pendingDelete?.let { group ->
        Button2Dialog(
            onDismissRequest = { pendingDelete = null },
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
            onConfirm = { onDelete(group) }
        )
    }
}

/**
 * 그룹 카드 리스트 + 드래그 순서변경. 놓으면 [onReorder] 로 전체 순서를 전달.
 *
 * 순서 상태([items])는 한 번만 만들고 그룹 목록이 바뀌면 값만 맞춘다. `remember(groups)` 로 새로 만들면, 한 번 끈 그룹의
 * 끌기 처리(`pointerInput`)가 예전 상태를 계속 붙들어 두 번째부터는 화면에 그리지 않는 상태만 바뀌었다 — 끄는 그룹이
 * 제자리로 튀다가 손을 떼야 옮겨졌다.
 */
@Composable
private fun ReorderableGroupList(
    groups: List<ChatGroup>,
    onReorder: (List<String>) -> Unit,
    onRename: (ChatGroup) -> Unit,
    onDelete: (ChatGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowHeight = 52.dp
    val rowSpacing = 10.dp
    val slotPx = with(LocalDensity.current) { (rowHeight + rowSpacing).toPx() }

    var items by remember { mutableStateOf(groups) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    LaunchedEffect(groups) { if (draggingId == null) items = groups }

    val unreadName = stringResource(Res.string.unread)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        items.forEach { group ->
            key(group.id) {
                val isDragging = group.id == draggingId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                        .border(1.dp, AppColors.Line, RoundedCornerShape(8.dp))
                        .background(AppColors.Bg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.hamburger_icon),
                        contentDescription = "Reorder",
                        tint = AppColors.TextSub,
                        modifier = Modifier
                            .size(24.dp)
                            .pointerInput(group.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingId = group.id
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffset = 0f
                                        onReorder(items.map { it.id })
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                        val curIdx = items.indexOfFirst { it.id == group.id }
                                        if (curIdx < 0) return@detectDragGestures
                                        val target =
                                            (curIdx + (dragOffset / slotPx).roundToInt())
                                                .coerceIn(0, items.lastIndex)
                                        if (target != curIdx) {
                                            items = items.toMutableList()
                                                .apply { add(target, removeAt(curIdx)) }
                                            dragOffset -= (target - curIdx) * slotPx
                                        }
                                    }
                                )
                            }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = if (group.kind == "1") unreadName else group.name,
                        color = AppColors.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    if (group.kind != "1") {
                        IconButton(onClick = { onRename(group) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                painter = painterResource(Res.drawable.edit_name_icon),
                                contentDescription = "Rename",
                                tint = AppColors.TextSub,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { onDelete(group) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                painter = painterResource(Res.drawable.trashcan_icon),
                                contentDescription = "Delete",
                                tint = AppColors.TextSub,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatRoomGroupManageContentPreview() {
    MaterialTheme {
        ChatRoomGroupManageContent(
            groups = listOf(
                ChatGroup(id = "unread", name = "UNREAD", kind = "1", sort = "0", roomIds = emptyList()),
                ChatGroup(id = "g1", name = "연구실", kind = "2", sort = "0001", roomIds = emptyList()),
                ChatGroup(id = "g2", name = "회의실", kind = "2", sort = "0002", roomIds = emptyList()),
                ChatGroup(id = "g3", name = "개발팀", kind = "2", sort = "0003", roomIds = emptyList()),
            ),
            onCreate = {},
            onRename = {},
            onDelete = {},
            onReorder = {}
        )
    }
}

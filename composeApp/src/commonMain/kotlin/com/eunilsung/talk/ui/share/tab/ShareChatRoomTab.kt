package com.eunilsung.talk.ui.share.tab

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.chat_tab_icon
import multiplatformtalk.composeapp.generated.resources.chatroom_list
import multiplatformtalk.composeapp.generated.resources.chatroom_list_search_hint
import com.eunilsung.talk.ui.main.tab.AppTab
import com.eunilsung.talk.ui.main.tab.AppTabOptions
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.ui.chatroomlist.item.ChatRoomItem
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.search.SearchBar
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

object ShareChatRoomTab : AppTab {
    override val key: String = "share_chatroom"

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            title = stringResource(Res.string.chatroom_list),
            icon = painterResource(Res.drawable.chat_tab_icon)
        )

    @Composable
    override fun Content() {
        ShareChatRoomTabContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareChatRoomTabContent() {
    if (LocalInspectionMode.current) return
    val controller = LocalShareChatRoomController.current
    val rooms by controller.rooms.collectAsState()
    val selected = controller.selectedRoomId

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    var query by remember { mutableStateOf("") }

    // heightOffset 을 key 로 쓰면 스크롤 프레임마다 화면 스코프가 무효화되고 코루틴도 재기동된다.
    // snapshotFlow 로 구독을 이 이펙트 안에 가두고, 임계값을 넘는 순간만 걸러낸다.
    LaunchedEffect(scrollBehavior) {
        snapshotFlow { scrollBehavior.state.heightOffset < TOP_BAR_COLLAPSE_THRESHOLD }
            .distinctUntilChanged()
            .filter { it }
            .collect { keyboardController?.hide() }
    }

    LaunchedEffect(query) {
        listState.scrollToItem(0)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopBarV1(
                title = stringResource(Res.string.chatroom_list),
                scrollBehavior = scrollBehavior,
                bottomContent = {
                    SearchBar(
                        searchTxt = query,
                        hintTxt = stringResource(Res.string.chatroom_list_search_hint),
                        onSearchTextChanged = { query = it },
                        onClearClick = { query = "" },
                        onSearchClick = { }
                    )
                },
                isShowTitle = false
            )
        }
    ) { padding ->
        val filteredRooms = remember(rooms, query) {
            if (query.isBlank()) rooms
            else rooms.filter { it.displayTitle.contains(query, ignoreCase = true) }
        }
        val pinnedRooms = remember(filteredRooms) {
            filteredRooms.filter { it.pinDate.isNotEmpty() }
        }
        val nonPinnedRooms = remember(filteredRooms) {
            filteredRooms.filter { it.pinDate.isEmpty() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Bg)
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = pinnedRooms,
                    key = { "share_pin_${it.id}" },
                    contentType = { "ShareChatRoomItem" }
                ) { room ->
                    ShareRoomRow(room = room, selectedId = selected, onSelect = controller.onSelect)
                }
                items(
                    items = nonPinnedRooms,
                    key = { "share_np_${it.id}" },
                    contentType = { "ShareChatRoomItem" }
                ) { room ->
                    ShareRoomRow(room = room, selectedId = selected, onSelect = controller.onSelect)
                }
            }
        }
    }
}

@Composable
private fun ShareRoomRow(
    room: ChatRoom.Item,
    selectedId: String?,
    onSelect: (ChatRoom.Item) -> Unit,
) {
    val display: ChatRoom.Item = room.copy(isSelect = room.id == selectedId)
    ChatRoomItem(
        item = display,
        onClick = { onSelect(room) },
        isToggleable = true
    )
}

/** 상단바가 이만큼 접히면 스크롤 의도로 보고 키보드를 내린다. */
private const val TOP_BAR_COLLAPSE_THRESHOLD = -10f

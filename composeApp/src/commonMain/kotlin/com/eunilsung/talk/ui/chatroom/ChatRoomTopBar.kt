package com.eunilsung.talk.ui.chatroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.Bookmark
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.chatroom.bookmark.BookmarkBottomSheetContent
import com.eunilsung.talk.ui.chatroom.item.ChatRoomSearchBar
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.sheet.LocalBottomSheetManager
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import kotlinx.coroutines.flow.StateFlow
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_left_icon
import multiplatformtalk.composeapp.generated.resources.bookmark_icon
import multiplatformtalk.composeapp.generated.resources.chat_search_hint
import multiplatformtalk.composeapp.generated.resources.hamburger_icon
import multiplatformtalk.composeapp.generated.resources.search_icon
import multiplatformtalk.composeapp.generated.resources.search_user_icon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource



/**
 * 대화방 상단바 — 일반 모드에서는 제목·인원수와 아이콘 5개, 검색 모드에서는 검색바를 보인다.
 *
 * @param onSenderPick 전송 주체 선택 팝업 열기(샘플 전용).
 * @param onDrawerOpen 우측 서랍 열기.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomTopBar(
    chatRoom: com.eunilsung.talk.domain.model.ChatRoom.Item?,
    isSearchMode: Boolean,
    searchState: ChatSearchState,
    senderOverride: com.eunilsung.talk.domain.model.User?,
    scrollBehavior: TopAppBarScrollBehavior,
    searchFocusRequester: FocusRequester,
    hideKeyboard: () -> Unit,
    handleBack: () -> Unit,
    isDrawerOpen: Boolean,
    bookmarksFlow: kotlinx.coroutines.flow.StateFlow<List<com.eunilsung.talk.domain.model.Bookmark>>,
    onSenderPick: () -> Unit,
    onDrawerOpen: () -> Unit,
    onAction: (ChatRoomActions) -> Unit,
) {
    val bottomSheet = LocalBottomSheetManager.current
            TopBarV1(
                title = if (isSearchMode) ""
                else "${chatRoom?.displayTitle} " + chatRoom?.displayUserCount?.let { if (it > 2) chatRoom.displayUserCount else "" },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (!isSearchMode) {
                        IconButton(
                            modifier = Modifier.size(28.dp),
                            onClick = handleBack
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.arrow_left_icon),
                                modifier = Modifier.size(28.dp),
                                contentDescription = when {
                                    isDrawerOpen -> "Close Drawer"
                                    else -> "Close"
                                },
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                actions = {
                    if (!isSearchMode) {
                        // 전송 주체 전환(샘플 전용).
                        IconButton(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (senderOverride != null) AppColors.UserSelectBg
                                    else AppColors.Transparent
                                ),
                            onClick = {
                                hideKeyboard()
                                onSenderPick()
                            }) {
                            Icon(
                                painter = painterResource(Res.drawable.search_user_icon),
                                modifier = Modifier.size(22.dp),
                                contentDescription = "Change Sender",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                        if (Config.ChatRoom.IS_BOOK_MARK_ENABLED) {
                            IconButton(
                                modifier = Modifier.size(28.dp),
                                onClick = {
                                    hideKeyboard()
                                    bottomSheet.custom {
                                        BookmarkBottomSheetContent(
                                            bookmarksFlow = bookmarksFlow,
                                            onItemClick = { bookmark ->
                                                onAction(ChatRoomActions.OnFocusChat(bookmark.chatId))
                                                bottomSheet.hide()
                                            },
                                            onRemove = { bookmark ->
                                                onAction(ChatRoomActions.OnDeleteBookmark(bookmark.chatId))
                                            },
                                        )
                                    }
                                }) {
                                Icon(
                                    painter = painterResource(Res.drawable.bookmark_icon),
                                    modifier = Modifier.size(28.dp),
                                    contentDescription = "BookMark Icon",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.size(10.dp))
                        }
                        if (Config.ChatRoom.IS_SEARCH_ENABLED) {
                            IconButton(
                                modifier = Modifier.size(28.dp),
                                onClick = {
                                    onAction(ChatRoomActions.OnEnterSearch)
                                }) {
                                Icon(
                                    painter = painterResource(Res.drawable.search_icon),
                                    modifier = Modifier.size(28.dp),
                                    contentDescription = "Search Icon",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.size(10.dp))
                        }
                        IconButton(
                            modifier = Modifier.size(28.dp),
                            onClick = {
                                onDrawerOpen()
                                hideKeyboard()
                            }) {
                            Icon(
                                painter = painterResource(Res.drawable.hamburger_icon),
                                modifier = Modifier.size(28.dp),
                                contentDescription = "Side Menu Icon",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.size(15.dp))
                    } else {
                        ChatRoomSearchBar(
                            searchedUser = searchState.searchedUser,
                            query = searchState.query,
                            hint = stringResource(Res.string.chat_search_hint),
                            onQueryChange = { onAction(ChatRoomActions.OnSearchQueryChange(it)) },
                            onSearch = { onAction(ChatRoomActions.OnSearch) },
                            onUserRemove = { onAction(ChatRoomActions.OnSearchUserRemove) },
                            onClearAll = {
                                onAction(ChatRoomActions.OnSearchQueryChange(""))
                                onAction(ChatRoomActions.OnSearchUserRemove)
                            },
                            onCancel = { onAction(ChatRoomActions.OnExitSearch) },
                            focusRequester = searchFocusRequester,
                        )
                    }
                },
                backgroundColor = AppColors.ChatRoomBg
            )
}

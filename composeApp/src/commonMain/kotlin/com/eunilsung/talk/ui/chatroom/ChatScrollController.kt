package com.eunilsung.talk.ui.chatroom

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


/**
 * 대화 목록 스크롤 제어 — UI 를 그리지 않고 효과만 건다.
 *
 * 다섯 갈래가 같은 [listState] 를 건드린다: 위/아래 끝 도달(과거·최신 더 읽기),
 * 진입 시 마커 배치, 검색 결과 포커스 이동, 내 전송·최신 로딩 후 하단 이동.
 * 한 함수에 모아 두어야 서로의 타이밍을 함께 볼 수 있다.
 */
@Composable
fun ChatScrollController(
    listState: androidx.compose.foundation.lazy.LazyListState,
    groupedChats: List<com.eunilsung.talk.domain.model.GroupedChat>,
    currentChatRoomId: String,
    searchState: ChatSearchState,
    entryScroll: SharedFlow<String?>?,
    mySendPush: SharedFlow<String>?,
    latestLoadedPush: SharedFlow<String>?,
    onAction: (ChatRoomActions) -> Unit,
) {
                LaunchedEffect(listState) {
                    snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    }
                        .distinctUntilChanged()
                        .collect { lastVisible ->
                            val total = listState.layoutInfo.totalItemsCount
                            if (lastVisible != null && total > 0 && lastVisible >= total - 3) {
                                onAction(ChatRoomActions.OnReachEnd)
                            }
                        }
                }

                LaunchedEffect(listState) {
                    snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
                    }
                        .distinctUntilChanged()
                        .collect { firstVisible ->
                            if (firstVisible != null && firstVisible <= 2) {
                                onAction(ChatRoomActions.OnReachStart)
                            }
                        }
                }

                val groupedForScroll = androidx.compose.runtime.rememberUpdatedState(groupedChats)
                if (entryScroll != null) {
                    LaunchedEffect(entryScroll, currentChatRoomId) {
                        entryScroll.collect { markerChatId ->
                            if (markerChatId != null) {
                                val alreadyVisible = listState.layoutInfo.visibleItemsInfo
                                    .any { it.key == markerChatId }
                                if (alreadyVisible) return@collect
                                /**
                                 * 마커를 뷰포트 [MARKER_VIEWPORT_RATIO] 지점에 놓는다.
                                 *
                                 * 두 번 스크롤하는 이유: 1차 scrollToItem 전에는 해당 항목이
                                 * measure 되기 전이라 visibleItemsInfo 에 없고, 높이를 알 수 없어
                                 * 오프셋을 계산할 수 없다. 먼저 붙여 높이를 얻은 뒤 다시 맞춘다.
                                 */
                                suspend fun placeMarker() {
                                    val chats = groupedForScroll.value
                                    val markerIdx = chats.indexOfFirst { it.chat.chatID == markerChatId }
                                    if (markerIdx < 0) return
                                    listState.scrollToItem(markerIdx)
                                    val li = listState.layoutInfo
                                    val itemH = li.visibleItemsInfo
                                        .firstOrNull { it.key == markerChatId }?.size ?: return
                                    val desired = (li.viewportSize.height * MARKER_VIEWPORT_RATIO).toInt()
                                    listState.scrollToItem(markerIdx, itemH - desired)
                                }
                                placeMarker()

                                withTimeoutOrNull(MARKER_SETTLE_TIMEOUT_MS) {
                                    snapshotFlow {
                                        val inList = groupedForScroll.value
                                            .any { it.chat.chatID == markerChatId }
                                        val visible = listState.layoutInfo.visibleItemsInfo
                                            .any { it.key == markerChatId }
                                        inList to visible
                                    }
                                        .distinctUntilChanged()
                                        .first { (inList, visible) ->
                                            if (inList && !visible && !listState.isScrollInProgress) {
                                                placeMarker()
                                            }
                                            visible
                                        }
                                }
                            } else {
                                listState.scrollToItem(0)
                                withTimeoutOrNull(LATEST_LOAD_TIMEOUT_MS) {
                                    snapshotFlow { listState.layoutInfo.totalItemsCount }
                                        .distinctUntilChanged()
                                        .collect { listState.scrollToItem(0) }
                                }
                            }
                        }
                    }
                }

                var lastHandledFocusKey by remember { mutableStateOf(-1) }
                LaunchedEffect(
                    searchState.focusChatId,
                    searchState.focusTriggerKey,
                    groupedChats
                ) {
                    val targetId = searchState.focusChatId ?: return@LaunchedEffect
                    if (searchState.focusTriggerKey == lastHandledFocusKey) return@LaunchedEffect
                    val idx = groupedChats.indexOfFirst { it.chat.chatID == targetId }
                    if (idx >= 0) {
                        listState.animateScrollToItem(idx.coerceAtLeast(0))
                        lastHandledFocusKey = searchState.focusTriggerKey
                    }
                }

                if (mySendPush != null) {
                    LaunchedEffect(mySendPush, currentChatRoomId) {
                        mySendPush.collect { sentRoomId ->
                            if (sentRoomId != currentChatRoomId) return@collect
                            listState.scrollToItem(0)
                            withTimeoutOrNull(SCROLL_SETTLE_TIMEOUT_MS) {
                                snapshotFlow { listState.layoutInfo.totalItemsCount }
                                    .distinctUntilChanged()
                                    .collect { listState.scrollToItem(0) }
                            }
                        }
                    }
                }

                if (latestLoadedPush != null) {
                    LaunchedEffect(latestLoadedPush, currentChatRoomId) {
                        latestLoadedPush.collect { loadedRoomId ->
                            if (loadedRoomId != currentChatRoomId) return@collect
                            delay(50)
                            listState.scrollToItem(0)
                        }
                    }
                }
}

/** 포커스한 대화를 뷰포트 어느 지점에 놓을지 — 0.4 = 위에서 40%. */
private const val MARKER_VIEWPORT_RATIO = 0.4f

/** 마커가 실제로 보일 때까지 기다리는 상한. 지연 measure 때문에 재시도가 필요하다. */
private const val MARKER_SETTLE_TIMEOUT_MS = 600L

/** 최신 대화 로딩 완료를 기다리는 상한. */
private const val LATEST_LOAD_TIMEOUT_MS = 1000L

/** 스크롤이 목표 위치에 안정될 때까지 기다리는 상한. */
private const val SCROLL_SETTLE_TIMEOUT_MS = 700L

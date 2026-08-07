package com.eunilsung.talk.ui.chatroom

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.SharedFlow

/**
 * 저장소가 밀어 주는 일회성 신호 묶음 — 전부 스크롤·배너 동작을 트리거한다.
 *
 * 각각 다른 시점에 오지만 소비처가 같아(대화 목록의 스크롤 제어) 한 덩어리로 넘긴다.
 * `@Preview` 에서는 신호가 없어야 하므로 기본값이 모두 null 이다.
 */
@Immutable
data class ChatRoomSignals(
    /** 상대 대화 도착 — 하단이면 따라가고, 올라가 있으면 새 대화 배너. */
    val newChat: SharedFlow<String>? = null,
    /** 내가 보냄 — 항상 최하단으로. */
    val mySend: SharedFlow<String>? = null,
    /** 최신 대화 로딩 완료 — 로딩 후 하단 정착. */
    val latestLoaded: SharedFlow<String>? = null,
    /** 방 진입 시 안읽음 마커로 스크롤할 대상 chatId. null 이면 하단. */
    val entryScroll: SharedFlow<String?>? = null,
)

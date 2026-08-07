package com.eunilsung.talk.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.domain.model.ChatRoom

interface ChatRoomListRepository {
    fun getChatRooms(): Flow<List<ChatRoom.Item>>
    suspend fun fetchChatRooms()

    /** 대화방 그룹 목록 — 대화함 상단 필터 칩. */
    fun getChatGroups(): Flow<List<ChatGroup>>

    /** 대화방을 그룹에 추가. */
    suspend fun addRoomToGroup(groupId: String, roomId: String)

    /** 대화방을 그룹에서 제거. */
    suspend fun removeRoomFromGroup(groupId: String, roomId: String)

    /** 그룹 생성 — Settings 의 그룹 칩 목록에 추가. */
    suspend fun createChatGroup(name: String)

    /** 그룹 이름 변경. */
    suspend fun renameChatGroup(groupId: String, newName: String)

    /** 그룹 삭제 — 소속 대화방은 그대로 두고 칩만 없앤다. */
    suspend fun deleteChatGroup(groupId: String)

    /** 그룹 순서변경. orderedGroupIds = 안읽음 포함 전체 새 순서. */
    suspend fun reorderChatGroups(orderedGroupIds: List<String>)

    /** [fetchChatRooms] 진행 중 여부 — 새로고침 표시용. */
    val isFetching: StateFlow<Boolean>

    /** 모든 대화방의 안읽음 카운트 합산 — 탭 뱃지 / 안읽음 필터 칩 카운트. */
    val unreadTotal: StateFlow<Int>

    /** 새 대화 도착 1회성 신호 (해당 방의 chatRoomId). */
    val newChatRoomPush: SharedFlow<String>

    /** 대화방 이름 변경. */
    suspend fun renameChatRoom(chatRoomId: String, newName: String): Boolean

    /** 대화방 알림 토글. isAlarm = "1" 끔(뮤트) / "0" 켬. */
    suspend fun setChatRoomAlarm(chatRoomId: String, isAlarm: String): Boolean

    /** 대화방 상단고정 토글 — Settings 에 저장. */
    suspend fun setChatRoomPin(chatRoomId: String, pinned: Boolean): Boolean

    /** 대화방 나가기. */
    suspend fun leaveChatRoom(chatRoomId: String): Boolean

    /** 단일 대화방 정보 갱신. 결과는 [getChatRooms] Flow 로 반영. */
    suspend fun fetchChatRoomInfo(chatRoomId: String)

    /**
     * 현재 열려있는 방을 통지 — 방 진입 시 해당 id, 이탈 시 null.
     *
     * 진입한 방의 안읽음·멘션 카운트를 0 으로 내린다. 호출부가 완료를 기다릴 수 있도록
     * `suspend` 로 둔다(내부에서 launch 하면 목록 갱신 시점이 비결정적이 된다).
     */
    suspend fun setActiveRoom(roomId: String?)
}

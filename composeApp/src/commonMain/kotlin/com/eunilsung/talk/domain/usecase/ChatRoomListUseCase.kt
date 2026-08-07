package com.eunilsung.talk.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.repository.ChatRoomListRepository

data class ChatRoomListUseCases(
    val getChatRooms: GetChatRoomsUseCase,
    val fetchChatRooms: FetchChatRoomsUseCase,
    val renameChatRoom: RenameChatRoomUseCase,
    val setChatRoomAlarm: SetChatRoomAlarmUseCase,
    val setChatRoomPin: SetChatRoomPinUseCase,
    val leaveChatRoom: LeaveChatRoomUseCase,
    val observeNewChatRoomPush: ObserveNewChatRoomPushUseCase,
    val observeChatRoomUnreadTotal: ObserveChatRoomUnreadTotalUseCase,
    val observeIsFetching: ObserveChatRoomFetchingUseCase,
    val observeChatGroups: ObserveChatGroupsUseCase,
    val addRoomToGroup: AddRoomToGroupUseCase,
    val removeRoomFromGroup: RemoveRoomFromGroupUseCase,
    val createChatGroup: CreateChatGroupUseCase,
    val renameChatGroup: RenameChatGroupUseCase,
    val deleteChatGroup: DeleteChatGroupUseCase,
    val reorderChatGroups: ReorderChatGroupsUseCase,
)

/** 그룹 생성. */
class CreateChatGroupUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    suspend operator fun invoke(name: String) = chatRoomListRepository.createChatGroup(name)
}

/** 그룹 이름변경. */
class RenameChatGroupUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    suspend operator fun invoke(groupId: String, newName: String) =
        chatRoomListRepository.renameChatGroup(groupId, newName)
}

/** 그룹 삭제. */
class DeleteChatGroupUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    suspend operator fun invoke(groupId: String) = chatRoomListRepository.deleteChatGroup(groupId)
}

/** 그룹 순서변경. */
class ReorderChatGroupsUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    suspend operator fun invoke(orderedGroupIds: List<String>) =
        chatRoomListRepository.reorderChatGroups(orderedGroupIds)
}

/** 대화방 그룹 목록 구독 — 그룹 칩 렌더 / 그룹 필터에 사용. */
class ObserveChatGroupsUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    operator fun invoke(): Flow<List<ChatGroup>> = chatRoomListRepository.getChatGroups()
}

/** 대화방을 그룹에 추가. */
class AddRoomToGroupUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    suspend operator fun invoke(groupId: String, roomId: String) =
        chatRoomListRepository.addRoomToGroup(groupId, roomId)
}

/** 대화방을 그룹에서 제거. */
class RemoveRoomFromGroupUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    suspend operator fun invoke(groupId: String, roomId: String) =
        chatRoomListRepository.removeRoomFromGroup(groupId, roomId)
}

/** 대화방 fetch 진행 여부 — 새로고침 버튼 회전 표시용. */
class ObserveChatRoomFetchingUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    operator fun invoke(): StateFlow<Boolean> = chatRoomListRepository.isFetching
}

class GetChatRoomsUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    operator fun invoke(): Flow<List<ChatRoom.Item>> = chatRoomListRepository.getChatRooms()
}

/** 모든 대화방 안읽음 합산 구독 — 대화 탭 뱃지 / 리스트 "안읽음" 칩 카운트. */
class ObserveChatRoomUnreadTotalUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    operator fun invoke(): StateFlow<Int> = chatRoomListRepository.unreadTotal
}

class FetchChatRoomsUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    suspend operator fun invoke() = chatRoomListRepository.fetchChatRooms()
}

/** 대화방 이름 변경 */
class RenameChatRoomUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    suspend operator fun invoke(chatRoomId: String, newName: String): Boolean =
        chatRoomListRepository.renameChatRoom(chatRoomId, newName)
}

/** 대화방 알림 on/off 토글 */
class SetChatRoomAlarmUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    suspend operator fun invoke(chatRoomId: String, isAlarm: String): Boolean =
        chatRoomListRepository.setChatRoomAlarm(chatRoomId, isAlarm)
}

/** 대화방 상단고정 토글 (로컬 전용) */
class SetChatRoomPinUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    suspend operator fun invoke(chatRoomId: String, pinned: Boolean): Boolean =
        chatRoomListRepository.setChatRoomPin(chatRoomId, pinned)
}

/** 대화방 나가기 */
class LeaveChatRoomUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    suspend operator fun invoke(chatRoomId: String): Boolean =
        chatRoomListRepository.leaveChatRoom(chatRoomId)
}

/** 새 대화 도착 1회성 신호 — Screen 이 자동 스크롤 분기에 사용 */
class ObserveNewChatRoomPushUseCase(private val chatRoomListRepository: ChatRoomListRepository) {
    operator fun invoke(): SharedFlow<String> = chatRoomListRepository.newChatRoomPush
}

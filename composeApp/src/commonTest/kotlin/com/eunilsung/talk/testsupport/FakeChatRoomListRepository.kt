package com.eunilsung.talk.testsupport

import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.repository.ChatRoomListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** 받아 둔 방 목록만 흉내 낸다. 바꾸는 호출은 아무 일도 하지 않는다. */
class FakeChatRoomListRepository(var rooms: List<ChatRoom.Item> = emptyList()) : ChatRoomListRepository {
    override fun getChatRooms(): Flow<List<ChatRoom.Item>> = flowOf(rooms)
    override suspend fun fetchChatRooms() = Unit
    override fun getChatGroups(): Flow<List<ChatGroup>> = flowOf(emptyList())
    override suspend fun addRoomToGroup(groupId: String, roomId: String) = Unit
    override suspend fun removeRoomFromGroup(groupId: String, roomId: String) = Unit
    override suspend fun createChatGroup(name: String) = Unit
    override suspend fun renameChatGroup(groupId: String, newName: String) = Unit
    override suspend fun deleteChatGroup(groupId: String) = Unit
    override suspend fun reorderChatGroups(orderedGroupIds: List<String>) = Unit
    override val isFetching: StateFlow<Boolean> = MutableStateFlow(false)
    override val unreadTotal: StateFlow<Int> = MutableStateFlow(0)
    override val newChatRoomPush: SharedFlow<String> = MutableSharedFlow()
    override suspend fun renameChatRoom(chatRoomId: String, newName: String) = true
    override suspend fun setChatRoomAlarm(chatRoomId: String, isAlarm: String) = true
    override suspend fun setChatRoomPin(chatRoomId: String, pinned: Boolean) = true
    override suspend fun leaveChatRoom(chatRoomId: String) = true
    override suspend fun fetchChatRoomInfo(chatRoomId: String) = Unit
    override suspend fun setActiveRoom(roomId: String?) = Unit
}

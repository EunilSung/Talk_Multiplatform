package com.eunilsung.talk.data.repository

import com.eunilsung.talk.Config
import com.eunilsung.talk.data.mapper.ServerChatMapper
import com.eunilsung.talk.data.remote.push.CurrentChatRoomTracker
import com.eunilsung.talk.data.remote.server.ServerEvents
import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.data.remote.server.valueOrNull
import com.eunilsung.talk.data.sample.LocalChatRoomListRepositoryImpl
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.repository.ChatRoomListRepository
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.shared.api.RoomDto
import com.eunilsung.talk.shared.api.ServerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 서버의 대화방 목록을 로컬 DB 에 받아 두고 보여 주는 저장소.
 *
 * 화면은 늘 로컬 DB 를 본다([local]). 이 클래스는 서버에서 받은 목록을 로컬에 반영하고, 방을 바꾸는
 * 요청을 서버로 보낸다. 서버가 완전한 목록을 돌려줬을 때만 로컬을 바꾸므로, 요청이 실패하면
 * 마지막으로 받은 목록이 그대로 남는다.
 *
 * 상단고정과 그룹 칩은 이 기기의 설정이라 [local] 이 그대로 맡는다.
 */
class ChatRoomListRepositoryImpl(
    private val local: LocalChatRoomListRepositoryImpl,
    private val server: TalkServer,
    private val serverEvents: ServerEvents,
    private val loginRepository: LoginRepository,
    private val mapper: ServerChatMapper,
) : ChatRoomListRepository by local {

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** 목록 반영을 한 번에 하나씩만 한다. 겹치면 늦게 끝난 옛 응답이 새 목록을 덮는다. */
    private val fetchMutex = Mutex()

    private val _isFetching = MutableStateFlow(false)
    override val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    private val _newChatRoomPush = MutableSharedFlow<String>(extraBufferCapacity = PUSH_BUFFER)
    override val newChatRoomPush: SharedFlow<String> = _newChatRoomPush.asSharedFlow()

    init {
        repositoryScope.launch {
            loginRepository.isLoggedIn.collectLatest { isLoggedIn ->
                if (isLoggedIn) serverEvents.listen()
            }
        }
        repositoryScope.launch { serverEvents.connected.collect { fetchChatRooms() } }
        repositoryScope.launch { serverEvents.events.collect { onServerEvent(it) } }
    }

    override suspend fun fetchChatRooms() {
        val myId = Config.MyInfo.userId
        if (myId.isBlank()) return
        fetchMutex.withLock {
            _isFetching.value = true
            try {
                val rooms = server.rooms().valueOrNull() ?: return
                /** 응답을 기다리는 사이 다른 계정으로 바뀌었으면 버린다. 남의 방이 내 목록에 들어가면 안 된다. */
                if (Config.MyInfo.userId != myId) return
                local.replaceRooms(rooms.map { toItem(it, myId) })
            } finally {
                _isFetching.value = false
            }
        }
    }

    override suspend fun fetchChatRoomInfo(chatRoomId: String) = fetchChatRooms()

    override suspend fun renameChatRoom(chatRoomId: String, newName: String): Boolean {
        if (newName.isBlank()) return false
        if (server.renameRoom(chatRoomId, newName) !is ServerResult.Success) return false
        fetchChatRooms()
        return true
    }

    override suspend fun setChatRoomAlarm(chatRoomId: String, isAlarm: String): Boolean {
        if (server.muteRoom(chatRoomId, isMuted = isAlarm == ALARM_OFF) !is ServerResult.Success) return false
        fetchChatRooms()
        return true
    }

    /** 서버에서 나간 뒤에 로컬의 방·대화·고정·그룹 소속을 지운다. 서버가 거절하면 아무것도 지우지 않는다. */
    override suspend fun leaveChatRoom(chatRoomId: String): Boolean {
        if (server.leaveRoom(chatRoomId) !is ServerResult.Success) return false
        local.leaveChatRoom(chatRoomId)
        return true
    }

    private suspend fun onServerEvent(event: ServerEvent) {
        when (event.type) {
            ServerEvent.TYPE_MESSAGE -> {
                fetchChatRooms()
                val isFromOther = event.message?.senderId != Config.MyInfo.userId
                if (isFromOther) _newChatRoomPush.tryEmit(event.roomId)
            }
            ServerEvent.TYPE_ROOM -> fetchChatRooms()
        }
    }

    /**
     * 지금 보고 있는 방은 안읽음을 0 으로 둔다.
     *
     * 방 안에서 새 대화를 받으면 화면이 곧바로 읽음을 보내지만, 그 요청이 서버에 닿기 전에 받은
     * 목록에는 아직 안읽음으로 잡혀 있다. 그대로 반영하면 보고 있는 방에 배지가 잠깐 떴다 사라진다.
     */
    private suspend fun toItem(room: RoomDto, myId: String): ChatRoom.Item {
        val item = mapper.toRoom(room, myId)
        val isViewing = loginRepository.isForeground.value &&
            CurrentChatRoomTracker.currentChatRoomId == room.id
        return if (isViewing) item.copy(unReadCount = "0") else item
    }

    private companion object {
        const val ALARM_OFF = "1"
        const val PUSH_BUFFER = 8
    }
}

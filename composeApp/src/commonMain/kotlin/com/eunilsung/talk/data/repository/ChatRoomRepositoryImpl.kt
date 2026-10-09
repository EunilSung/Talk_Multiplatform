package com.eunilsung.talk.data.repository

import com.eunilsung.talk.Config
import com.eunilsung.talk.data.mapper.ServerChatMapper
import com.eunilsung.talk.data.remote.server.ServerEvents
import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.data.remote.server.valueOrNull
import com.eunilsung.talk.data.sample.LocalChatRoomRepositoryImpl
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.repository.ChatRoomRepository
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.ServerEvent
import com.eunilsung.talk.util.ChatIdUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 서버와 대화를 주고받는 저장소.
 *
 * 화면은 늘 로컬 DB 를 본다([local]). 이 클래스는 서버에서 받은 대화를 로컬에 채우고, 내가 쓴 대화를
 * 서버로 보낸다. 서버가 완결된 응답을 준 것만 로컬에 반영하므로, 요청이 실패하면 가지고 있던 대화가
 * 그대로 남는다.
 *
 * 공지·책갈피·공감·회수·파일은 아직 서버에 연동하지 않았다. [local] 이 이 기기 안에서만 처리한다.
 */
class ChatRoomRepositoryImpl(
    private val local: LocalChatRoomRepositoryImpl,
    private val server: TalkServer,
    private val serverEvents: ServerEvents,
    private val mapper: ServerChatMapper,
    database: AppDatabase,
) : ChatRoomRepository by local {

    private val dbQueries = database.appDatabaseQueries
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * 이번 실행에서 서버와 맞춰 본 방들.
     *
     * 알림으로 온 대화는 이 방들에만 저장한다. 맞춰 보지 않은 방에 알림 대화만 끼워 넣으면 그 앞의
     * 대화가 비게 되고, 다음에 "마지막 대화 이후"를 받을 때 그 빈 구간을 영영 건너뛴다.
     */
    private val syncedRooms = MutableStateFlow<Set<String>>(emptySet())

    /** 지금 서버로 보내는 중인 대화 id. 여기에 없는데 '전송 중'인 대화는 지난 실행이 남긴 것이다. */
    private val sendingIds = MutableStateFlow<Set<String>>(emptySet())

    private val _roomUsersChanged = MutableSharedFlow<String>(extraBufferCapacity = PUSH_BUFFER)
    override val roomUsersChanged: SharedFlow<String> = _roomUsersChanged.asSharedFlow()

    private val _selfLeftPush = MutableSharedFlow<String>(extraBufferCapacity = PUSH_BUFFER)
    override val selfLeftPush: SharedFlow<String> = _selfLeftPush.asSharedFlow()

    init {
        repositoryScope.launch { serverEvents.events.collect { onServerEvent(it) } }
        repositoryScope.launch {
            /** 끊겨 있던 동안의 대화는 알림으로 다시 오지 않는다. 붙을 때마다 열어 본 방을 다시 맞춘다. */
            serverEvents.connected.collect {
                syncedRooms.value.forEach { roomId -> syncFromServer(roomId) }
            }
        }
    }

    override suspend fun fetchChats(chatRoomId: String) {
        syncFromServer(chatRoomId)
        local.fetchChats(chatRoomId)
        failStaleSendingChats(chatRoomId)
    }

    override suspend fun sendTextChat(
        chatRoomId: String,
        text: String,
        replyTarget: Chat.Item?,
        emoticonId: String?,
    ) {
        if (Config.MyInfo.userId.isBlank() || chatRoomId.isBlank()) return
        if (text.isBlank() && emoticonId.isNullOrBlank()) return

        val chat = local.buildTextChat(chatRoomId, text, replyTarget, emoticonId, Chat.Statue.SENDING)
        sendingIds.update { it + chat.chatID }
        local.appendMyChat(chatRoomId, chat)
        deliver(chatRoomId, chat)
    }

    /**
     * 실패한 대화를 다시 보낸다.
     *
     * id 를 바꾸지 않는다. 서버가 같은 id 를 한 번만 받으므로, 지난번에 응답만 잃어버린 경우에도
     * 대화가 두 번 생기지 않는다.
     */
    override suspend fun resendFailedChat(chatRoomId: String, chatId: String) {
        val failed = local.getChats(chatRoomId).first()
            .firstOrNull { it.chatID == chatId && it.chatStatue == Chat.Statue.FAIL }
            ?: return
        val retry = failed.copy(
            chatStatue = Chat.Statue.SENDING,
            date = ChatIdUtils.formatChatDate(ChatIdUtils.nowAsLocalDateTime()),
        )
        sendingIds.update { it + retry.chatID }
        local.storeChats(chatRoomId, listOf(retry))
        deliver(chatRoomId, retry)
    }

    /** 서버에 읽은 자리를 알리고, 목록의 안읽음 배지를 내린다. 서버에 닿지 못해도 배지는 내린다. */
    override suspend fun markChatAsRead(chatRoomId: String, lastChatId: String): Boolean {
        if (chatRoomId.isBlank() || lastChatId.isBlank()) return false
        val result = server.markRead(chatRoomId, lastChatId)
        local.markChatAsRead(chatRoomId, lastChatId)
        return result is ServerResult.Success
    }

    override suspend fun refreshChatUnreadCounts(chatRoomId: String) {
        val oldest = local.getChats(chatRoomId).first()
            .firstOrNull { it.chatStatue == Chat.Statue.COMPLETE }
            ?.chatID
            ?: return
        val counts = server.unreadCounts(chatRoomId, oldest).valueOrNull() ?: return
        local.updateUnreadCounts(chatRoomId, counts.associate { it.messageId to it.unreadCount.toString() })
    }

    /**
     * 로컬에 있는 마지막 대화 이후를 서버에서 받아 채운다. 로컬이 비어 있으면 최근 한 쪽만 받는다.
     *
     * 한 쪽이 가득 차서 오면 더 있다는 뜻이므로 이어서 받는다. 도중에 실패하면 거기서 멈춘다 —
     * 받은 데까지는 앞에서부터 빈틈없이 이어져 있어, 다음에 이어 받으면 된다.
     */
    private suspend fun syncFromServer(chatRoomId: String) {
        val myId = Config.MyInfo.userId
        if (myId.isBlank() || chatRoomId.isBlank()) return
        var cursor = withContext(Dispatchers.Default) {
            dbQueries.selectLastCompleteChatId(myId, chatRoomId).executeAsOneOrNull()
        }
        repeat(MAX_SYNC_PAGES) {
            val page = server.messages(chatRoomId, afterId = cursor, limit = PAGE_SIZE).valueOrNull() ?: return
            if (page.isNotEmpty()) local.storeChats(chatRoomId, page.map { mapper.toChat(it) })
            syncedRooms.update { it + chatRoomId }
            if (cursor == null || page.size < PAGE_SIZE) return
            cursor = page.last().id
        }
    }

    /** 서버로 보내고, 결과에 따라 '전송 중'을 완료 또는 실패로 바꾼다. */
    private suspend fun deliver(chatRoomId: String, chat: Chat.Item) {
        val settled = when (val result = server.sendMessage(chatRoomId, mapper.toRequest(chat))) {
            is ServerResult.Success -> mapper.toChat(result.value)
            else -> chat.copy(chatStatue = Chat.Statue.FAIL)
        }
        local.storeChats(chatRoomId, listOf(settled))
        sendingIds.update { it - chat.chatID }
    }

    /**
     * 지난 실행이 '전송 중'으로 남긴 대화를 실패로 돌린다.
     *
     * 보내는 도중 앱이 꺼지면 결과를 적지 못한다. 서버에 들어갔다면 방금 받아 온 대화가 같은 id 로
     * 덮어썼을 것이므로, 아직 '전송 중'인 것은 서버에 없는 것이다. 사용자가 다시 보낼 수 있게 한다.
     */
    private suspend fun failStaleSendingChats(chatRoomId: String) {
        val stale = local.getChats(chatRoomId).first()
            .filter { it.chatStatue == Chat.Statue.SENDING && it.chatID !in sendingIds.value }
        if (stale.isNotEmpty()) {
            local.storeChats(chatRoomId, stale.map { it.copy(chatStatue = Chat.Statue.FAIL) })
        }
    }

    private suspend fun onServerEvent(event: ServerEvent) {
        when (event.type) {
            ServerEvent.TYPE_MESSAGE -> onMessage(event)
            ServerEvent.TYPE_READ -> if (event.roomId in syncedRooms.value) refreshChatUnreadCounts(event.roomId)
            ServerEvent.TYPE_ROOM -> onRoomChanged(event.roomId)
        }
    }

    private suspend fun onMessage(event: ServerEvent) {
        val message = event.message ?: return
        if (event.roomId !in syncedRooms.value) return
        val chat = mapper.toChat(message)
        /** 내가 보낸 대화의 메아리는 저장만 한다. 새 대화 신호를 내면 화면이 받은 대화처럼 반응한다. */
        if (chat.isMe) local.storeChats(event.roomId, listOf(chat)) else local.appendMyChat(event.roomId, chat)
    }

    /** 방 정보가 바뀌었다. 내가 더 이상 참여자가 아니면 나간 것으로, 아니면 참여자가 바뀐 것으로 알린다. */
    private suspend fun onRoomChanged(chatRoomId: String) {
        when (val result = server.room(chatRoomId)) {
            is ServerResult.Success -> _roomUsersChanged.tryEmit(chatRoomId)
            is ServerResult.Rejected -> if (result.code == ChatErrorCode.ROOM_NOT_FOUND) {
                syncedRooms.update { it - chatRoomId }
                _selfLeftPush.tryEmit(chatRoomId)
            }
            ServerResult.Unreachable -> Unit
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        /** 한 번에 따라잡는 최대 쪽 수. 이보다 밀려 있으면 다음 진입 때 이어서 받는다. */
        const val MAX_SYNC_PAGES = 20
        const val PUSH_BUFFER = 8
    }
}

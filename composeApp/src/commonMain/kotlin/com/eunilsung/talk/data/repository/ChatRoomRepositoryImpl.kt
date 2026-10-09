package com.eunilsung.talk.data.repository

import com.eunilsung.talk.Config
import com.eunilsung.talk.data.mapper.ServerChatMapper
import com.eunilsung.talk.data.local.FileMetadataResolver
import com.eunilsung.talk.data.remote.server.ServerEvents
import com.eunilsung.talk.data.remote.server.ServerFileStore
import com.eunilsung.talk.data.remote.server.ServerResult
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.data.remote.server.valueOrNull
import com.eunilsung.talk.data.sample.LocalChatRoomRepositoryImpl
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.domain.model.Bookmark
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.Notice
import com.eunilsung.talk.domain.repository.ChatRoomRepository
import com.eunilsung.talk.shared.api.ChatErrorCode
import com.eunilsung.talk.shared.api.MAX_FILE_BYTES
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.NoticeChangeResponse
import com.eunilsung.talk.shared.api.ServerEvent
import com.eunilsung.talk.util.ChatIdUtils
import com.eunilsung.talk.util.stripMentionTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 서버와 대화를 주고받는 저장소.
 *
 * 화면은 늘 로컬 DB 를 본다([local]). 이 클래스는 서버에서 받은 대화를 로컬에 채우고, 내가 쓴 대화를
 * 서버로 보낸다. 서버가 완결된 응답을 준 것만 로컬에 반영하므로, 요청이 실패하면 가지고 있던 대화가
 * 그대로 남는다.
 *
 * 받은 파일은 기기 캐시에 내려받아 둔다. 화면이 파일을 기기 안 경로로 열기 때문이다([ServerFileStore]).
 */
class ChatRoomRepositoryImpl(
    private val local: LocalChatRoomRepositoryImpl,
    private val server: TalkServer,
    private val serverEvents: ServerEvents,
    private val mapper: ServerChatMapper,
    private val fileStore: ServerFileStore,
    private val fileMetadataResolver: FileMetadataResolver,
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

    private val downloadPermits = Semaphore(MAX_PARALLEL_DOWNLOADS)

    private val _roomUsersChanged = MutableSharedFlow<String>(extraBufferCapacity = PUSH_BUFFER)
    override val roomUsersChanged: SharedFlow<String> = _roomUsersChanged.asSharedFlow()

    private val _selfLeftPush = MutableSharedFlow<String>(extraBufferCapacity = PUSH_BUFFER)
    override val selfLeftPush: SharedFlow<String> = _selfLeftPush.asSharedFlow()

    private val _currentNotice = MutableStateFlow<Notice?>(null)
    override val currentNotice: StateFlow<Notice?> = _currentNotice.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    override val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    /** [currentNotice] 와 [bookmarks] 가 지금 어느 방의 것인지. */
    private var extrasRoomId: String? = null

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
        requestNotice(chatRoomId)
        fetchBookmarks(chatRoomId)
    }

    override fun clearCurrentRoom(chatRoomId: String) {
        local.clearCurrentRoom(chatRoomId)
        showRoomExtras(null)
    }

    /**
     * 공감을 누른다. 켤지 끌지는 서버가 정하고, 서버가 돌려준 대화를 그대로 반영한다.
     *
     * 화면에 먼저 그려 두지 않는다. 여러 사람이 동시에 누를 때 각자 계산한 결과를 그려 두면
     * 서버의 답과 어긋났다가 고쳐지며 깜빡인다.
     */
    override suspend fun sendEmpathy(chatRoomId: String, targetChatId: String, empathyType: String) {
        val updated = server.toggleReaction(chatRoomId, targetChatId, empathyType).valueOrNull() ?: return
        local.storeChats(chatRoomId, listOf(mapper.toChat(updated)))
    }

    /** 서버가 회수를 받아 준 뒤에 화면에서 거둔다. 서버에 닿지 못했는데 거둔 것처럼 보이면 안 된다. */
    override suspend fun recallChat(chatRoomId: String, targetChatId: String) {
        val recalled = server.recallMessage(chatRoomId, targetChatId).valueOrNull() ?: return
        local.storeChats(chatRoomId, listOf(mapper.toChat(recalled)))
    }

    override suspend fun requestNotice(chatRoomId: String) {
        showRoomExtras(chatRoomId)
        val notice = server.notice(chatRoomId).valueOrNull() ?: return
        if (extrasRoomId == chatRoomId) _currentNotice.value = mapper.toNotice(notice)
    }

    override suspend fun addNotice(chatRoomId: String, content: String) {
        if (content.isBlank()) return
        applyNoticeChange(chatRoomId, server.setNotice(chatRoomId, content.stripMentionTags()).valueOrNull())
    }

    override suspend fun deleteNotice(chatRoomId: String) {
        applyNoticeChange(chatRoomId, server.deleteNotice(chatRoomId).valueOrNull())
    }

    override suspend fun fetchBookmarks(chatRoomId: String) {
        showRoomExtras(chatRoomId)
        val bookmarks = server.bookmarks(chatRoomId).valueOrNull() ?: return
        if (extrasRoomId == chatRoomId) _bookmarks.value = bookmarks.map { mapper.toBookmark(chatRoomId, it) }
    }

    override suspend fun addBookmark(chatRoomId: String, chat: Chat.Item) {
        if (server.addBookmark(chatRoomId, chat.chatID) is ServerResult.Success) fetchBookmarks(chatRoomId)
    }

    override suspend fun deleteBookmark(chatRoomId: String, chatId: String) {
        if (server.removeBookmark(chatRoomId, chatId) is ServerResult.Success) fetchBookmarks(chatRoomId)
    }

    /** 공지가 바뀐 결과를 반영한다 — 공지 띠와, 그 일을 알리는 대화. 서버가 받아 주지 않았으면 그대로 둔다. */
    private suspend fun applyNoticeChange(chatRoomId: String, change: NoticeChangeResponse?) {
        change ?: return
        if (extrasRoomId == chatRoomId) _currentNotice.value = mapper.toNotice(change.notice)
        local.storeChats(chatRoomId, listOf(mapper.toChat(change.message)))
    }

    /**
     * 공지와 책갈피가 어느 방의 것인지 바꾼다. 방이 바뀌면 앞 방의 것을 비운다.
     *
     * 비우지 않으면 새 방의 조회가 실패했을 때 앞 방의 공지가 그대로 남아 엉뚱한 방에 걸려 보인다.
     */
    private fun showRoomExtras(chatRoomId: String?) {
        if (extrasRoomId == chatRoomId) return
        extrasRoomId = chatRoomId
        _currentNotice.value = null
        _bookmarks.value = emptyList()
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
        if (retry.chatType in FILE_CHAT_TYPES) deliverFile(chatRoomId, retry) else deliver(chatRoomId, retry)
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
        val lastKnown = withContext(Dispatchers.Default) {
            dbQueries.selectLastCompleteChatId(myId, chatRoomId).executeAsOneOrNull()
        }
        var cursor = lastKnown
        repeat(MAX_SYNC_PAGES) {
            val page = server.messages(chatRoomId, afterId = cursor, limit = PAGE_SIZE).valueOrNull() ?: return
            if (page.isNotEmpty()) storeFromServer(chatRoomId, page)
            syncedRooms.update { it + chatRoomId }
            if (cursor == null || page.size < PAGE_SIZE) {
                if (lastKnown != null) refreshRecentChats(chatRoomId)
                return
            }
            cursor = page.last().id
        }
    }

    /**
     * 이미 받아 둔 최근 대화의 상태(공감·회수·안읽음 수)를 서버 것으로 맞춘다.
     *
     * "마지막 대화 이후"만 받으면 그 앞 대화에 생긴 변화를 놓친다. 앱이 꺼져 있던 동안 누가 공감을 누르거나
     * 대화를 회수했을 수 있으므로, 최근 한 쪽은 통째로 다시 받아 덮어쓴다.
     */
    private suspend fun refreshRecentChats(chatRoomId: String) {
        val recent = server.messages(chatRoomId, limit = PAGE_SIZE).valueOrNull() ?: return
        if (recent.isNotEmpty()) storeFromServer(chatRoomId, recent)
    }

    /**
     * 파일을 올린 뒤 대화로 보낸다. 고르는 즉시 '전송 중'으로 보이고, 둘 다 끝나야 완료가 된다.
     *
     * 파일만 올라가고 대화가 못 간 경우에도 실패로 남긴다 — 상대에게는 아무것도 보이지 않았기 때문이다.
     */
    override suspend fun sendFile(chatRoomId: String, path: String) {
        val chat = local.buildFileChat(chatRoomId, path, Chat.Statue.SENDING) ?: return
        sendingIds.update { it + chat.chatID }
        local.appendMyChat(chatRoomId, chat)
        deliverFile(chatRoomId, chat)
    }

    private suspend fun deliverFile(chatRoomId: String, chat: Chat.Item) {
        val settled = uploadAndSend(chatRoomId, chat) ?: chat.copy(chatStatue = Chat.Statue.FAIL)
        local.storeChats(chatRoomId, listOf(settled))
        sendingIds.update { it - chat.chatID }
    }

    /** 성공하면 서버가 확정한 대화, 어느 단계든 실패하면 null. */
    private suspend fun uploadAndSend(chatRoomId: String, chat: Chat.Item): Chat.Item? {
        val source = chat.localPath.ifBlank { chat.imagePath }
        val bytes = fileMetadataResolver.readBytes(source) ?: return null
        if (bytes.size > MAX_FILE_BYTES) return null
        val file = server.uploadFile(chatRoomId, chat.originalFileName.ifBlank { DEFAULT_FILE_NAME }, bytes)
            .valueOrNull() ?: return null
        /** 내가 올린 파일은 원본이 이미 기기에 있다. 경로를 적어 두어 다시 내려받지 않게 한다. */
        fileStore.remember(file.id, source)
        val message = server.sendMessage(chatRoomId, mapper.toFileRequest(chat, file)).valueOrNull() ?: return null
        return mapper.toChat(message)
    }

    /** 서버에서 받은 대화들을 로컬에 반영하고, 아직 기기에 없는 파일은 뒤에서 받는다. */
    private suspend fun storeFromServer(chatRoomId: String, messages: List<MessageDto>) {
        local.storeChats(chatRoomId, messages.map { mapper.toChat(it) })
        downloadMissingFiles(chatRoomId, messages)
    }

    /**
     * 파일 대화 중 기기에 파일이 없는 것을 내려받아 말풍선에 연결한다.
     *
     * 대화 저장을 기다리게 하지 않고 뒤에서 받는다. 다 받으면 그 대화에 경로만 채워 다시 저장한다 —
     * 받는 사이 달라졌을 수 있는 공감·안읽음 수를 옛 값으로 되돌리지 않기 위해서다.
     */
    private fun downloadMissingFiles(chatRoomId: String, messages: List<MessageDto>) {
        messages.forEach { message ->
            val fileId = message.payload?.fileId ?: return@forEach
            if (message.isRecalled || fileStore.localPath(fileId) != null) return@forEach
            repositoryScope.launch {
                val path = downloadPermits.withPermit {
                    fileStore.download(fileId, message.payload?.fileName ?: DEFAULT_FILE_NAME)
                } ?: return@launch
                val current = local.getChats(chatRoomId).first().firstOrNull { it.chatID == message.id }
                    ?: return@launch
                if (!current.isRecalled) {
                    local.storeChats(chatRoomId, listOf(current.copy(imagePath = path, localPath = path)))
                }
            }
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
            ServerEvent.TYPE_MESSAGE_UPDATED -> onMessageUpdated(event)
            ServerEvent.TYPE_NOTICE -> if (event.roomId == extrasRoomId) requestNotice(event.roomId)
        }
    }

    /** 공감이 눌리거나 회수된 대화를 고쳐 쓴다. 새 대화가 아니므로 새 대화 신호는 내지 않는다. */
    private suspend fun onMessageUpdated(event: ServerEvent) {
        val message = event.message ?: return
        if (event.roomId !in syncedRooms.value) return
        local.storeChats(event.roomId, listOf(mapper.toChat(message)))
    }

    private suspend fun onMessage(event: ServerEvent) {
        val message = event.message ?: return
        if (event.roomId !in syncedRooms.value) return
        val chat = mapper.toChat(message)
        /** 내가 보낸 대화의 메아리는 저장만 한다. 새 대화 신호를 내면 화면이 받은 대화처럼 반응한다. */
        if (chat.isMe) local.storeChats(event.roomId, listOf(chat)) else local.appendMyChat(event.roomId, chat)
        downloadMissingFiles(event.roomId, listOf(message))
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
        /** 파일을 한꺼번에 몇 개까지 받을지. 방에 들어갈 때 사진이 많아도 연결을 독차지하지 않게 한다. */
        const val MAX_PARALLEL_DOWNLOADS = 2
        const val DEFAULT_FILE_NAME = "file"
        val FILE_CHAT_TYPES = setOf(Chat.Type.IMAGE, Chat.Type.VIDEO, Chat.Type.FILE)
    }
}

package com.eunilsung.talk.data.sample

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.eunilsung.talk.domain.repository.SenderOverrideRepository
import com.eunilsung.talk.Config
import com.eunilsung.talk.data.local.FileMetadataResolver
import com.eunilsung.talk.data.mapper.ChatMapper
import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.db.BookmarkEntity
import com.eunilsung.talk.db.NoticeEntity
import com.eunilsung.talk.domain.model.Bookmark
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.PolishStyle
import com.eunilsung.talk.domain.model.Emoticon
import com.eunilsung.talk.domain.model.EmpathyChat
import com.eunilsung.talk.domain.model.Notice
import com.eunilsung.talk.domain.model.ReplyChat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.repository.ChatRoomListRepository
import com.eunilsung.talk.domain.repository.ChatRoomRepository
import com.eunilsung.talk.util.ChatIdUtils
import com.eunilsung.talk.util.Log
import com.eunilsung.talk.util.formatFileSize
import com.eunilsung.talk.util.EMOTICON_LABEL
import com.eunilsung.talk.util.chatDisplayText
import com.eunilsung.talk.util.stripMentionTags
import com.eunilsung.talk.util.UserListCodec

/** `ChatEntity` 에 직접 읽고 쓰는 대화 내용 저장소. */
class LocalChatRoomRepositoryImpl(
    private val chatMapper: ChatMapper,
    private val chatRoomMapper: ChatRoomMapper,
    private val chatRoomListRepository: ChatRoomListRepository,
    private val fileMetadataResolver: FileMetadataResolver,
    private val senderOverride: SenderOverrideRepository,
    database: AppDatabase,
    /** 빈 방에 시연용 대화를 채울지. 서버 모드에서는 끈다 — 대화는 서버에서만 온다. */
    private val seedsSampleChats: Boolean = true,
) : ChatRoomRepository {

    private val dbQueries = database.appDatabaseQueries
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** 방별 대화 목록 — 화면이 구독하는 단일 출처. */
    private val chatsByRoom = MutableStateFlow<Map<String, List<Chat.Item>>>(emptyMap())

    /**
     * 방마다 화면에 올려 둘 대화 수. 위로 올려 더 불러올 때마다 늘어나고, 방을 닫으면 기본값으로 돌아간다.
     * 여러 스레드에서 읽고 쓰지만 방 하나를 여는 동안 한 화면만 건드린다.
     */
    private val loadLimits = mutableMapOf<String, Long>()

    private val _newChatPush = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val newChatPush: SharedFlow<String> = _newChatPush.asSharedFlow()

    private val _mySendPush = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val mySendPush: SharedFlow<String> = _mySendPush.asSharedFlow()

    private val _latestLoadedPush = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val latestLoadedPush: SharedFlow<String> = _latestLoadedPush.asSharedFlow()

    /** 참여자 변동·강제 퇴장 신호 — 로컬에선 방출하지 않는다. */
    override val roomUsersChanged: SharedFlow<String> =
        MutableSharedFlow<String>().asSharedFlow()
    override val selfLeftPush: SharedFlow<String> =
        MutableSharedFlow<String>().asSharedFlow()

    private val _currentNotice = MutableStateFlow<Notice?>(null)
    override val currentNotice: StateFlow<Notice?> = _currentNotice.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    override val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()


    override fun getChats(chatRoomId: String): Flow<List<Chat.Item>> =
        chatsByRoom.map { it[chatRoomId].orEmpty() }

    override suspend fun fetchChats(chatRoomId: String) = withContext(Dispatchers.Default) {
        val myId = Config.MyInfo.userId
        if (myId.isBlank() || chatRoomId.isBlank()) return@withContext

        if (seedsSampleChats &&
            dbQueries.selectRecentChatsByRoom(myId, chatRoomId, LOAD_LIMIT).executeAsList().isEmpty()
        ) {
            seedChats(myId, chatRoomId)
        }
        reload(myId, chatRoomId)
        _currentNotice.value = loadNotice(myId, chatRoomId)
        _bookmarks.value = loadBookmarks(myId, chatRoomId)
    }

    /** 더 불러올 과거 대화 없음. */
    override suspend fun fetchMoreChats(chatRoomId: String): Boolean = false

    override suspend fun fetchNewerChats(chatRoomId: String): List<Chat.Item> = emptyList()

    override suspend fun loadLatestChats(chatRoomId: String) {
        fetchChats(chatRoomId)
        _latestLoadedPush.tryEmit(chatRoomId)
    }

    override suspend fun loadChatWithContext(
        chatRoomId: String,
        chatId: String,
        contextSize: Int,
    ): Boolean = chatsByRoom.value[chatRoomId]?.any { it.chatID == chatId } == true

    override suspend fun fetchChatRoomUsers(chatRoomId: String): List<User> =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            val room = dbQueries.selectChatRoomsByMyId(myId).executeAsList()
                .firstOrNull { it.roomId == chatRoomId }
                ?.let { chatRoomMapper.toModel(it) }
                ?: return@withContext emptyList()

            UserListCodec.decode(room.totalUserList).map { (id, name) ->
                val account = TestAccounts.find(id)
                User(
                    id = id,
                    name = name.ifBlank { account?.userName ?: id },
                    departmentName = account?.organName,
                    positionName = account?.positionName,
                    presencePc = account?.pcStatus,
                    presenceMobile = account?.mobileStatus,
                )
            }
        }

    override suspend fun searchChats(
        chatRoomId: String,
        query: String,
        userId: String,
        dateFrom: String,
        dateTo: String,
    ): List<Pair<String, String>> {
        val chats = chatsByRoom.value[chatRoomId].orEmpty()
        return chats.filter { chat ->
            (query.isBlank() || chat.chatContent.contains(query, ignoreCase = true)) &&
                (userId.isBlank() || chat.user.id == userId) &&
                (dateFrom.isBlank() || chat.date >= dateFrom) &&
                (dateTo.isBlank() || chat.date <= dateTo)
        }.map { it.chatID to it.date }
    }

    override fun clearCurrentRoom(chatRoomId: String) {
        loadLimits.remove(chatRoomId)
        _currentNotice.value = null
        _bookmarks.value = emptyList()
    }

    // ---- 전송 ----

    override suspend fun sendTextChat(
        chatRoomId: String,
        text: String,
        replyTarget: Chat.Item?,
        emoticonId: String?,
    ) = withContext(Dispatchers.Default) {
        val myId = Config.MyInfo.userId
        if (myId.isBlank() || chatRoomId.isBlank()) return@withContext
        if (text.isBlank() && emoticonId.isNullOrBlank()) return@withContext

        /** 서버가 없으므로 곧바로 전송 완료 상태로 둔다. */
        val chat = buildTextChat(chatRoomId, text, replyTarget, emoticonId, Chat.Statue.COMPLETE)
        appendMyChat(chatRoomId, chat)
        Log.message("[Chat/Local] sent ${chat.chatID} in $chatRoomId")
    }

    /** 내가 쓴 텍스트·답장·이모티콘 대화를 [statue] 상태로 만든다. 저장하지는 않는다. */
    internal fun buildTextChat(
        chatRoomId: String,
        text: String,
        replyTarget: Chat.Item?,
        emoticonId: String?,
        statue: String,
    ): Chat.Item {
        val sender = senderOf(chatRoomId)
        val hasEmoticon = !emoticonId.isNullOrBlank()
        // 답장이면 REPLY, 이모티콘이면 EMOTICON, 아니면 TEXT.
        return Chat.Item(
            chatID = ChatIdUtils.generateChatId(sender.id),
            chatType = when {
                replyTarget != null -> Chat.Type.REPLY
                hasEmoticon -> Chat.Type.EMOTICON
                else -> Chat.Type.TEXT
            },
            chatContent = text,
            chatStatue = statue,
            date = nowChatDate(),
            unReadCount = "0",
            user = sender,
            replyChat = replyTarget?.let {
                ReplyChat(
                    chatID = it.chatID,
                    chatType = it.chatType,
                    // 인용문은 멘션 태그를 벗기고, 이모티콘 전용 대화는 대체 문구를 쓴다.
                    chatContent = it.chatContent.ifBlank {
                        if (it.chatType == Chat.Type.EMOTICON) EMOTICON_LABEL else ""
                    }.stripMentionTags(),
                    user = it.user,
                    emoticon = it.emoticon,
                    imagePath = it.imagePath,
                )
            } ?: ReplyChat(),
            emoticon = if (hasEmoticon) Emoticon(id = emoticonId!!) else Emoticon(),
        )
    }

    /** 재전송 대상 없음. */
    override suspend fun resendFailedChat(chatRoomId: String, chatId: String) = Unit

    override suspend fun deleteFailedChat(chatRoomId: String, chatId: String) =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            dbQueries.deleteChatById(myId = myId, chatRoomId = chatRoomId, chatId = chatId)
            reload(myId, chatRoomId)
        }

    override suspend fun sendFile(chatRoomId: String, path: String) {
        val chat = buildFileChat(chatRoomId, path, Chat.Statue.COMPLETE) ?: return
        appendMyChat(chatRoomId, chat)
        Log.message("[Chat/Local] sent file '${chat.originalFileName}' (${chat.chatType}) in $chatRoomId")
    }

    /** 내가 고른 파일로 대화를 [statue] 상태로 만든다. 저장하지는 않는다. 파일 정보를 읽지 못하면 null. */
    internal suspend fun buildFileChat(chatRoomId: String, path: String, statue: String): Chat.Item? =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            if (myId.isBlank() || path.isBlank()) return@withContext null

            // 메타데이터에서 실제 표시명을 얻는다.
            val meta = runCatching { fileMetadataResolver.resolve(path) }.getOrElse {
                Log.message("[Chat/Local] sendFile: metadata resolve failed: ${it.message}")
                return@withContext null
            }
            val name = meta.originalName.ifBlank { "file" }
            val chatType = Chat.typeFromPath(name.takeIf { it.contains('.') } ?: path)
            val sender = senderOf(chatRoomId)
            val chatId = ChatIdUtils.generateChatId(sender.id)

            // iOS 갤러리 항목은 PHAsset 식별자라 그대로 저장하면 말풍선에서 열리지 않는다.
            // 실제 파일로 확보해 두 진입점(대화방·공유)을 한 곳에서 처리한다.
            val storedPath = runCatching {
                fileMetadataResolver.materializeForDisplay(path, "${chatId.substringBefore('.')}_$name")
            }.getOrElse {
                Log.message("[Chat/Local] sendFile: materialize failed: ${it.message}")
                path
            }

            Chat.Item(
                chatID = chatId,
                chatType = chatType,
                // title 은 요약 자리.
                title = when (chatType) {
                    Chat.Type.IMAGE -> "(사진)"
                    Chat.Type.VIDEO -> "(동영상)"
                    else -> "(파일)"
                },
                // 파일 말풍선 부제 — 이미지는 "(사진)", 그 외는 파일 크기.
                chatContent = if (chatType == Chat.Type.IMAGE) "(사진)" else formatFileSize(meta.sizeBytes),
                chatStatue = statue,
                date = nowChatDate(),
                unReadCount = "0",
                user = sender,
                originalFileName = name,
                imageSize = meta.widthHeight,
                localPath = storedPath,
                // imagePath — 파일 상세 시트가 열기/다운로드에 쓰는 값(비면 시트가 안 뜬다).
                imagePath = storedPath,
            )
        }

    override suspend fun sendEmpathy(
        chatRoomId: String,
        targetChatId: String,
        empathyType: String,
    ) = withContext(Dispatchers.Default) {
        val myId = Config.MyInfo.userId
        val target = chatsByRoom.value[chatRoomId]?.firstOrNull { it.chatID == targetChatId }
            ?: return@withContext

        val me = senderOf(chatRoomId)
        val current = target.empathy
        // 같은 반응을 다시 누르면 취소(토글).
        val already = current.listFor(empathyType).any { it.id == me.id }
        val updated = current.withoutMe(me.id).let { cleared ->
            if (already) cleared else cleared.plus(empathyType, me)
        }.copy(chatID = targetChatId)

        persist(myId, chatRoomId, target.copy(empathy = updated))
        reload(myId, chatRoomId)
    }

    override suspend fun recallChat(chatRoomId: String, targetChatId: String) =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            // isRecalled 는 "1"/"0" 으로 저장.
            dbQueries.updateChatRecalled(
                isRecalled = "1", myId = myId, chatRoomId = chatRoomId, chatId = targetChatId
            )
            reload(myId, chatRoomId)
            Log.message("[Chat/Local] recalled $targetChatId")
        }

    /**
     * 읽음 처리 — 서버가 없으니 방의 안읽음/멘션 카운트를 0 으로 내리는 것이 곧 읽음이다.
     *
     * [lastChatId] 까지 읽었다는 개념(다른 참여자에게 전파)은 로컬에서 의미가 없어 쓰지 않는다.
     */
    override suspend fun markChatAsRead(chatRoomId: String, lastChatId: String): Boolean {
        if (chatRoomId.isBlank()) return false
        chatRoomListRepository.setActiveRoom(chatRoomId)
        return true
    }

    override suspend fun refreshChatUnreadCounts(chatRoomId: String) = Unit

    // ---- 공지 ----

    override suspend fun requestNotice(chatRoomId: String) = withContext(Dispatchers.Default) {
        _currentNotice.value = loadNotice(Config.MyInfo.userId, chatRoomId)
    }

    /** 서버가 없는 로컬 모드에는 번역할 모델이 없다. */
    override suspend fun translateChat(chatRoomId: String, chatId: String, languageCode: String): String? = null

    /** 서버가 없는 로컬 모드에는 다듬을 모델이 없다. */
    override suspend fun polishText(text: String, style: PolishStyle): String? = null

    override suspend fun addNotice(chatRoomId: String, content: String) {
        if (content.isBlank()) return
        val myId = Config.MyInfo.userId
        val sender = senderOf(chatRoomId)
        val notice = Notice(
            noticeId = ChatIdUtils.generateChatId(sender.id),
            chatRoomId = chatRoomId,
            content = content.stripMentionTags(),
            ownerId = sender.id,
            ownerName = sender.name,
            ownerPosition = TestAccounts.find(sender.id)?.positionName.orEmpty(),
            date = nowChatDate(),
        )
        withContext(Dispatchers.Default) {
            dbQueries.insertNotice(
                NoticeEntity(
                    myId = myId,
                    chatRoomId = chatRoomId,
                    noticeId = notice.noticeId,
                    content = notice.content,
                    ownerId = notice.ownerId,
                    ownerName = notice.ownerName,
                    ownerPosition = notice.ownerPosition,
                    date = notice.date,
                )
            )
        }
        _currentNotice.value = notice
        appendNoticeChat(myId, chatRoomId, added = true, noticeContent = notice.content)
        Log.message("[Chat/Local] notice added in $chatRoomId")
    }

    override suspend fun deleteNotice(chatRoomId: String) {
        val myId = Config.MyInfo.userId
        // 삭제 전 내용 확보(미리보기용).
        val previous = withContext(Dispatchers.Default) {
            loadNotice(myId, chatRoomId).also {
                dbQueries.deleteNotice(myId = myId, chatRoomId = chatRoomId)
            }
        }
        val removed = previous?.content.orEmpty()
        _currentNotice.value = null
        appendNoticeChat(myId, chatRoomId, added = false, noticeContent = removed)
        Log.message("[Chat/Local] notice deleted in $chatRoomId")
    }

    /** 공지 등록/삭제 시 대화방에 남는 시스템 대화. */
    private suspend fun appendNoticeChat(
        myId: String,
        chatRoomId: String,
        added: Boolean,
        noticeContent: String,
    ) = withContext(Dispatchers.Default) {
            if (myId.isBlank() || chatRoomId.isBlank()) return@withContext
            val sender = senderOf(chatRoomId)
            val chat = Chat.Item(
                chatID = ChatIdUtils.generateChatId(sender.id),
                chatType = Chat.Type.NOTICE,
                // 말풍선과 목록 미리보기 모두 이 본문을 쓴다.
                chatContent = noticeContent,
                // 등록/삭제 구분 — 문구는 표시하는 쪽에서 고른다.
                title = if (added) Notice.ACTION_ADD else Notice.ACTION_DELETE,
                chatStatue = Chat.Statue.COMPLETE,
                date = nowChatDate(),
                unReadCount = "0",
                user = sender,
            )
            appendMyChat(chatRoomId, chat)
        }

    // ---- 책갈피 ----

    override suspend fun fetchBookmarks(chatRoomId: String) = withContext(Dispatchers.Default) {
        _bookmarks.value = loadBookmarks(Config.MyInfo.userId, chatRoomId)
    }

    override suspend fun addBookmark(chatRoomId: String, chat: Chat.Item) =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            dbQueries.insertBookmark(
                BookmarkEntity(
                    myId = myId,
                    chatRoomId = chatRoomId,
                    chatId = chat.chatID,
                    content = chat.chatContent.stripMentionTags(),
                    date = chat.date,
                    userId = chat.user.id,
                    userName = chat.user.name,
                )
            )
            _bookmarks.value = loadBookmarks(myId, chatRoomId)
        }

    override suspend fun deleteBookmark(chatRoomId: String, chatId: String) =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            dbQueries.deleteBookmark(myId = myId, chatRoomId = chatRoomId, chatId = chatId)
            _bookmarks.value = loadBookmarks(myId, chatRoomId)
        }

    // ---- 내부 ----

    private fun loadNotice(myId: String, chatRoomId: String): Notice? =
        dbQueries.selectNotice(myId, chatRoomId).executeAsOneOrNull()?.let {
            Notice(
                noticeId = it.noticeId,
                chatRoomId = it.chatRoomId,
                content = it.content,
                ownerId = it.ownerId,
                ownerName = it.ownerName,
                ownerPosition = it.ownerPosition,
                date = it.date,
            )
        }

    private fun loadBookmarks(myId: String, chatRoomId: String): List<Bookmark> =
        dbQueries.selectBookmarksByRoom(myId, chatRoomId).executeAsList().map {
            Bookmark(
                chatId = it.chatId,
                chatRoomId = it.chatRoomId,
                content = it.content,
                date = it.date,
                userId = it.userId,
                userName = it.userName,
            )
        }

    private fun seedChats(myId: String, chatRoomId: String) {
        val room = dbQueries.selectChatRoomsByMyId(myId).executeAsList()
            .firstOrNull { it.roomId == chatRoomId }
            ?.let { chatRoomMapper.toModel(it) }
            ?: return

        val seeded = TestChats.seed(room, myId)
        if (seeded.isEmpty()) return
        dbQueries.transaction {
            seeded.forEach { dbQueries.insertChat(chatMapper.toEntity(myId, chatRoomId, it)) }
        }
        Log.message("[Chat/Local] seeded ${seeded.size} chats into $chatRoomId")
    }

    /** 대화를 방에 추가하는 공통 경로 — 저장 → 목록 갱신 → 미리보기 동기화 → 신호 방출. */
    suspend fun appendMyChat(chatRoomId: String, chat: Chat.Item) = withContext(Dispatchers.Default) {
        val myId = Config.MyInfo.userId
        if (myId.isBlank() || chatRoomId.isBlank()) return@withContext
        persist(myId, chatRoomId, chat)
        reload(myId, chatRoomId)
        if (chat.isMe) _mySendPush.tryEmit(chatRoomId)
        _newChatPush.tryEmit(chatRoomId)
    }

    /**
     * 대화 여러 건을 저장하고 화면 목록을 다시 읽는다. 새 대화 신호는 내지 않는다.
     *
     * 서버에서 받아 온 대화를 채워 넣거나, 이미 있는 대화의 상태(전송 중 → 완료·실패)를 고칠 때 쓴다.
     * 같은 id 는 덮어쓴다.
     */
    internal suspend fun storeChats(chatRoomId: String, chats: List<Chat.Item>) = withContext(Dispatchers.Default) {
        val myId = Config.MyInfo.userId
        if (myId.isBlank() || chatRoomId.isBlank()) return@withContext
        dbQueries.transaction { chats.forEach { persist(myId, chatRoomId, it) } }
        reload(myId, chatRoomId)
    }

    /**
     * 더 오래된 대화를 저장하고, 화면에 올려 둔 범위를 그만큼 넓혀 다시 읽는다.
     *
     * 범위를 넓히지 않으면 저장만 되고 화면에는 여전히 최근 대화만 보인다.
     */
    internal suspend fun storeOlderChats(chatRoomId: String, chats: List<Chat.Item>) = withContext(Dispatchers.Default) {
        val myId = Config.MyInfo.userId
        if (myId.isBlank() || chatRoomId.isBlank() || chats.isEmpty()) return@withContext
        dbQueries.transaction { chats.forEach { persist(myId, chatRoomId, it) } }
        loadLimits[chatRoomId] = (loadLimits[chatRoomId] ?: LOAD_LIMIT) + chats.size
        reload(myId, chatRoomId)
    }

    /** 대화별 안읽음 수(대화 id → 수)를 고치고 화면 목록을 다시 읽는다. 로컬에 없는 대화는 건너뛴다. */
    internal suspend fun updateUnreadCounts(chatRoomId: String, counts: Map<String, String>) =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            if (myId.isBlank() || chatRoomId.isBlank() || counts.isEmpty()) return@withContext
            dbQueries.transaction {
                counts.forEach { (chatId, count) ->
                    dbQueries.updateChatUnreadCount(
                        unReadCount = count, myId = myId, chatRoomId = chatRoomId, chatId = chatId
                    )
                }
            }
            reload(myId, chatRoomId)
        }

    private fun persist(myId: String, chatRoomId: String, chat: Chat.Item) {
        dbQueries.insertChat(chatMapper.toEntity(myId, chatRoomId, chat))
    }

    /** DB 를 다시 읽어 해당 방 목록을 갱신하고 리스트 미리보기까지 맞춘다. */
    private suspend fun reload(myId: String, chatRoomId: String) {
        val chats = dbQueries.selectRecentChatsByRoom(myId, chatRoomId, loadLimits[chatRoomId] ?: LOAD_LIMIT)
            .executeAsList()
            .map { chatMapper.toModel(it) }
            .sortedWith(compareBy({ it.date }, { Chat.Statue.displayPriority(it.chatStatue) }))
        chatsByRoom.value = chatsByRoom.value + (chatRoomId to chats)
        syncRoomPreview(myId, chatRoomId, chats.lastOrNull())
    }

    /** 대화방 리스트의 마지막 대화 미리보기를 실제 마지막 대화에 맞춘다. */
    private suspend fun syncRoomPreview(myId: String, chatRoomId: String, last: Chat.Item?) {
        last ?: return
        val entity = dbQueries.selectChatRoomsByMyId(myId).executeAsList()
            .firstOrNull { it.roomId == chatRoomId } ?: return
        val room = chatRoomMapper.toModel(entity)

        val preview = previewOf(last)
        val date = last.date.substringBeforeLast(':')
        if (room.lastChatContent == preview && room.lastChatDate == date) return

        dbQueries.insertChatRoom(
            chatRoomMapper.toEntity(
                myId,
                room.copy(
                    lastChatContent = preview,
                    lastChatDate = date,
                    lastChatID = last.chatID,
                )
            )
        )
        chatRoomListRepository.fetchChatRoomInfo(chatRoomId)
    }

    /**
     * 대화방 리스트 표기 — 공용 함수에 위임.
     *
     * 문자열 리소스 조회가 실패해도(예: 리소스 환경이 없는 유닛테스트) 대화 저장까지
     * 실패하면 안 되므로 본문으로 되돌린다.
     */
    private suspend fun previewOf(chat: Chat.Item): String =
        runCatching { chatDisplayText(chat) }.getOrElse {
            Log.message("[Chat/Local] preview build failed: ${it.message}")
            chat.chatContent
        }

    private fun myName(): String = Config.MyInfo.userName.ifBlank { Config.MyInfo.userId }

    /** 이 대화에 작성자로 박을 사용자 — 기본은 나, override 지정 시 그 사람. */
    private fun senderOf(chatRoomId: String): User =
        senderOverride.senderFor(chatRoomId)
            ?: User(id = Config.MyInfo.userId, name = myName())


    /** `yyyy-MM-dd HH:mm:ss:SSS` — ChatEntity/DateUtils 가 쓰는 대화 시각 포맷. */
    private fun nowChatDate(): String {
        val dt = kotlinx.datetime.Instant
            .fromEpochMilliseconds(kotlin.time.Clock.System.now().toEpochMilliseconds())
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val y = dt.year.toString().padStart(4, '0')
        val mo = dt.monthNumber.toString().padStart(2, '0')
        val d = dt.dayOfMonth.toString().padStart(2, '0')
        val h = dt.hour.toString().padStart(2, '0')
        val mi = dt.minute.toString().padStart(2, '0')
        val s = dt.second.toString().padStart(2, '0')
        val ms = (dt.nanosecond / 1_000_000).toString().padStart(3, '0')
        return "$y-$mo-$d $h:$mi:$s:$ms"
    }

    private companion object {
        const val LOAD_LIMIT = 200L

    }
}

// ---- EmpathyChat 헬퍼 ----

private fun EmpathyChat.listFor(type: String): List<User> = when (type) {
    "0" -> empathy0
    "1" -> empathy1
    "2" -> empathy2
    "3" -> empathy3
    "4" -> empathy4
    else -> empathy5
}

/** 모든 반응 목록에서 나를 제거. */
private fun EmpathyChat.withoutMe(myId: String): EmpathyChat = EmpathyChat(
    chatID = chatID,
    empathy0 = empathy0.filterNot { it.id == myId },
    empathy1 = empathy1.filterNot { it.id == myId },
    empathy2 = empathy2.filterNot { it.id == myId },
    empathy3 = empathy3.filterNot { it.id == myId },
    empathy4 = empathy4.filterNot { it.id == myId },
    empathy5 = empathy5.filterNot { it.id == myId },
)

private fun EmpathyChat.plus(type: String, user: User): EmpathyChat = when (type) {
    "0" -> copy(empathy0 = empathy0 + user)
    "1" -> copy(empathy1 = empathy1 + user)
    "2" -> copy(empathy2 = empathy2 + user)
    "3" -> copy(empathy3 = empathy3 + user)
    "4" -> copy(empathy4 = empathy4 + user)
    else -> copy(empathy5 = empathy5 + user)
}

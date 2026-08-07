package com.eunilsung.talk.data.testdata

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.eunilsung.talk.Config
import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.repository.ChatRoomListRepository
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.util.Log

/**
 * 대화방 목록 저장소 — 본문은 SQLDelight, pinDate/그룹 칩은 [Settings] 에 저장.
 */
class LocalChatRoomListRepositoryImpl(
    private val loginRepository: LoginRepository,
    private val settings: Settings,
    private val mapper: ChatRoomMapper,
    database: AppDatabase,
) : ChatRoomListRepository {

    private val dbQueries = database.appDatabaseQueries
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _chatRooms = MutableStateFlow<List<ChatRoom.Item>>(emptyList())
    private val _chatGroups = MutableStateFlow<List<ChatGroup>>(emptyList())
    private val _newChatRoomPush = MutableSharedFlow<String>()
    private val json = Json { ignoreUnknownKeys = true }
    private var activeRoomId: String? = null

    init {
        repositoryScope.launch {
            loginRepository.isLoggedIn.filter { it }.collect { seedIfNeeded() }
        }
        repositoryScope.launch {
            loginRepository.isLoggedIn.drop(1).filter { !it }.collect {
                Log.message("[ChatRoomList/Local] clear view on logout")
                _chatRooms.value = emptyList()
                _chatGroups.value = emptyList()
                activeRoomId = null
            }
        }
    }

    override fun getChatRooms(): Flow<List<ChatRoom.Item>> = _chatRooms.asStateFlow()

    override fun getChatGroups(): Flow<List<ChatGroup>> = _chatGroups.asStateFlow()

    override suspend fun fetchChatRooms() = seedIfNeeded()

    /** 단일 방 갱신 — DB 를 다시 읽는다. */
    override suspend fun fetchChatRoomInfo(chatRoomId: String) = withContext(Dispatchers.Default) {
        val myId = Config.MyInfo.userId
        if (myId.isNotBlank()) refreshFromDb(myId)
    }

    override val isFetching: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override val newChatRoomPush: SharedFlow<String> = _newChatRoomPush.asSharedFlow()

    override val unreadTotal: StateFlow<Int> = _chatRooms
        .map { rooms -> rooms.sumOf { it.unReadCount.toIntOrNull() ?: 0 } }
        .stateIn(repositoryScope, SharingStarted.Eagerly, 0)

    /** 방 진입 시 안읽음을 0 으로 내린다. */
    override suspend fun setActiveRoom(roomId: String?) = withContext(Dispatchers.Default) {
        activeRoomId = roomId
        if (roomId == null) return@withContext
        val myId = Config.MyInfo.userId
        if (myId.isBlank()) return@withContext
        val current = _chatRooms.value.firstOrNull { it.id == roomId } ?: return@withContext
        if (current.unReadCount == "0" && current.mentionCount == "0") return@withContext

        dbQueries.updateChatRoomUnreadCount(unReadCount = "0", myId = myId, roomId = roomId)
        // 멘션 카운트 전용 쿼리가 없어 행 전체를 다시 써서 함께 0 으로 맞춘다.
        dbQueries.insertChatRoom(
            mapper.toEntity(myId, current.copy(unReadCount = "0", mentionCount = "0"))
        )
        refreshFromDb(myId)
    }

    override suspend fun renameChatRoom(chatRoomId: String, newName: String): Boolean =
        withContext(Dispatchers.Default) {
            if (newName.isBlank()) return@withContext false
            val myId = Config.MyInfo.userId
            if (!roomExists(myId, chatRoomId)) return@withContext false

            dbQueries.updateChatRoomTitle(title = newName, myId = myId, roomId = chatRoomId)
            refreshFromDb(myId)
            Log.message("[ChatRoomList/Local] rename $chatRoomId → '$newName'")
            true
        }

    override suspend fun setChatRoomAlarm(chatRoomId: String, isAlarm: String): Boolean =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            if (!roomExists(myId, chatRoomId)) return@withContext false

            dbQueries.updateChatRoomAlarm(isAlarm = isAlarm, myId = myId, roomId = chatRoomId)
            refreshFromDb(myId)
            Log.message("[ChatRoomList/Local] alarm $chatRoomId → $isAlarm")
            true
        }

    /** 상단고정은 [Settings] 에만 저장한다. */
    override suspend fun setChatRoomPin(chatRoomId: String, pinned: Boolean): Boolean =
        withContext(Dispatchers.Default) {
            if (chatRoomId.isBlank()) return@withContext false
            val myId = Config.MyInfo.userId
            val key = pinKey(myId, chatRoomId)
            if (pinned) {
                settings.putString(key, kotlin.time.Clock.System.now().toEpochMilliseconds().toString())
            } else {
                settings.remove(key)
            }
            refreshFromDb(myId)
            Log.message("[ChatRoomList/Local] pin $chatRoomId → $pinned")
            true
        }

    /** 대화방 나가기 — 행과 대화 내용을 바로 삭제한다. */
    override suspend fun leaveChatRoom(chatRoomId: String): Boolean =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            if (!roomExists(myId, chatRoomId)) return@withContext false

            dbQueries.transaction {
                dbQueries.deleteChatRoomById(myId = myId, roomId = chatRoomId)
                dbQueries.deleteChatsByRoom(myId, chatRoomId)
            }
            settings.remove(pinKey(myId, chatRoomId))
            updateChatGroups(myId) { groups ->
                groups.map { it.copy(roomIds = it.roomIds.filterNot { id -> id == chatRoomId }) }
            }
            refreshFromDb(myId)
            Log.message("[ChatRoomList/Local] leave $chatRoomId — row deleted")
            true
        }

    // ---- 대화방 그룹(칩) ----

    override suspend fun addRoomToGroup(groupId: String, roomId: String) {
        updateChatGroups(Config.MyInfo.userId) { groups ->
            groups.map {
                if (it.id != groupId || roomId in it.roomIds) it
                else it.copy(roomIds = it.roomIds + roomId)
            }
        }
    }

    override suspend fun removeRoomFromGroup(groupId: String, roomId: String) {
        updateChatGroups(Config.MyInfo.userId) { groups ->
            groups.map {
                if (it.id != groupId) it
                else it.copy(roomIds = it.roomIds.filterNot { id -> id == roomId })
            }
        }
    }

    override suspend fun createChatGroup(name: String) {
        if (name.isBlank()) return
        updateChatGroups(Config.MyInfo.userId) { groups ->
            val sort = ((groups.maxOfOrNull { it.sort.toIntOrNull() ?: 0 } ?: 0) + 1).toString()
            val id = "cg_local_$sort"
            groups + ChatGroup(id = id, name = name, kind = "2", sort = sort, roomIds = emptyList())
        }
        Log.message("[ChatRoomList/Local] createChatGroup '$name'")
    }

    override suspend fun renameChatGroup(groupId: String, newName: String) {
        if (newName.isBlank()) return
        updateChatGroups(Config.MyInfo.userId) { groups ->
            groups.map { if (it.id == groupId) it.copy(name = newName) else it }
        }
    }

    override suspend fun deleteChatGroup(groupId: String) {
        updateChatGroups(Config.MyInfo.userId) { groups -> groups.filterNot { it.id == groupId } }
    }

    override suspend fun reorderChatGroups(orderedGroupIds: List<String>) {
        updateChatGroups(Config.MyInfo.userId) { groups ->
            groups
                .map { group ->
                    val index = orderedGroupIds.indexOf(group.id)
                    group to if (index < 0) Int.MAX_VALUE else index
                }
                .sortedBy { it.second }
                .mapIndexed { index, (group, _) -> group.copy(sort = (index + 1).toString()) }
        }
    }

    // ---- 내부 ----

    /** 해당 계정의 대화방이 DB 에 없을 때만 초기 배치를 넣는다. */
    private suspend fun seedIfNeeded() = withContext(Dispatchers.Default) {
        val myId = Config.MyInfo.userId
        if (myId.isBlank()) return@withContext

        if (dbQueries.selectChatRoomsByMyId(myId).executeAsList().isEmpty()) {
            val seeded = TestChatRooms.seed(myId)
            dbQueries.transaction {
                seeded.forEach { dbQueries.insertChatRoom(mapper.toEntity(myId, it)) }
            }
            // 시드의 pinDate 는 Settings 소관이므로 옮겨 적는다.
            seeded.filter { it.pinDate.isNotBlank() }.forEach {
                settings.putString(pinKey(myId, it.id), it.pinDate)
            }
            Log.message("[ChatRoomList/Local] seeded into DB — ${seeded.size} rooms (me=$myId)")
        }

        if (settings.getStringOrNull(chatGroupsKey(myId)) == null) {
            writeChatGroups(myId, TestChatRooms.seedGroups())
        }
        _chatGroups.value = readChatGroups(myId)
        refreshFromDb(myId)
    }

    /** DB 를 읽고 pinDate 만 Settings 값으로 덮은 뒤 정렬. */
    private fun refreshFromDb(myId: String) {
        val rooms = dbQueries.selectChatRoomsByMyId(myId).executeAsList().map { entity ->
            val item = mapper.toModel(entity)
            item.copy(pinDate = settings.getStringOrNull(pinKey(myId, item.id)).orEmpty())
        }
        _chatRooms.value = sortByPinThenDate(rooms)
    }

    private fun roomExists(myId: String, roomId: String): Boolean =
        dbQueries.selectChatRoomsByMyId(myId).executeAsList().any { it.roomId == roomId }

    /** 고정 우선 → pinDate desc → 마지막 대화시각 desc. */
    private fun sortByPinThenDate(rooms: List<ChatRoom.Item>): List<ChatRoom.Item> =
        rooms.sortedWith(
            compareByDescending<ChatRoom.Item> { it.pinDate.isNotEmpty() }
                .thenByDescending { it.pinDate.toLongOrNull() ?: 0L }
                .thenByDescending { it.lastChatDate }
        )

    private fun updateChatGroups(myId: String, transform: (List<ChatGroup>) -> List<ChatGroup>) {
        if (myId.isBlank()) return
        val next = transform(readChatGroups(myId))
        writeChatGroups(myId, next)
        _chatGroups.value = next
    }

    private fun readChatGroups(myId: String): List<ChatGroup> {
        val raw = settings.getStringOrNull(chatGroupsKey(myId)) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<ChatGroupRecord>>(raw).map {
                ChatGroup(it.id, it.name, it.kind, it.sort, it.roomIds)
            }
        }.getOrElse {
            Log.message("[ChatRoomList/Local] chat group decode failed: ${it.message}")
            emptyList()
        }
    }

    private fun writeChatGroups(myId: String, groups: List<ChatGroup>) {
        val raw = json.encodeToString(
            groups.map { ChatGroupRecord(it.id, it.name, it.kind, it.sort, it.roomIds) }
        )
        settings.putString(chatGroupsKey(myId), raw)
    }

    private fun pinKey(myId: String, roomId: String): String = "pin_${myId}_$roomId"

    private fun chatGroupsKey(myId: String): String = "local_chat_groups_$myId"

    /** [ChatGroup] 의 저장 전용 직렬화 형태. */
    @Serializable
    private data class ChatGroupRecord(
        val id: String,
        val name: String,
        val kind: String,
        val sort: String,
        val roomIds: List<String>,
    )
}

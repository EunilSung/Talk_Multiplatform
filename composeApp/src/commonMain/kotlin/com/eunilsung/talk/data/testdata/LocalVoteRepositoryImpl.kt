package com.eunilsung.talk.data.testdata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.eunilsung.talk.Config
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.db.VoteEntity
import com.eunilsung.talk.domain.model.VoteData
import com.eunilsung.talk.domain.model.VoteDataItem
import com.eunilsung.talk.domain.model.VoteForm
import com.eunilsung.talk.domain.model.VoteSummary
import com.eunilsung.talk.domain.model.VoteVoter
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.model.Vote
import com.eunilsung.talk.domain.model.VoteComplete
import com.eunilsung.talk.domain.model.VoteResultItem
import com.eunilsung.talk.domain.model.VoteSetting
import com.eunilsung.talk.domain.repository.VoteRepository
import com.eunilsung.talk.util.ChatIdUtils
import com.eunilsung.talk.util.Log

/** `VoteEntity` 에 직접 읽고 쓰는 투표 저장소. */
class LocalVoteRepositoryImpl(
    private val chatRoomRepository: LocalChatRoomRepositoryImpl,
    database: AppDatabase,
) : VoteRepository {

    private val dbQueries = database.appDatabaseQueries
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchVotes(chatRoomId: String): List<VoteSummary> =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            if (myId.isBlank()) return@withContext emptyList()

            dbQueries.selectVotesByRoom(myId, chatRoomId).executeAsList()
                .map { it.toData() }
                .map { data ->
                    VoteSummary(
                        id = data.id,
                        title = data.title,
                        itemCount = data.items.size,
                        participantCount = data.participantCount,
                        isClosed = data.isClosed,
                        endTime = data.endTime,
                        isMine = data.writeUserId.equals(myId, ignoreCase = true),
                        hasVoted = data.hasVoted(myId),
                    )
                }
        }

    override suspend fun fetchVote(chatRoomId: String, voteId: String): VoteData? =
        withContext(Dispatchers.Default) { load(chatRoomId, voteId) }

    override suspend fun createVote(chatRoomId: String, form: VoteForm): Result<String> =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            if (myId.isBlank()) return@withContext Result.failure(IllegalStateException("not logged in"))
            if (form.title.isBlank()) return@withContext Result.failure(IllegalArgumentException("title is blank"))

            val items = form.items
                .filter { it.content.isNotBlank() }
                .mapIndexed { index, item ->
                    VoteDataItem(idx = index, seq = index, content = item.content, nVote = 0, writeUserId = myId)
                }
            if (items.size < 2) return@withContext Result.failure(IllegalArgumentException("need 2+ items"))

            val voteId = ChatIdUtils.generateChatId(myId)
            val data = VoteData(
                id = voteId,
                title = form.title,
                isClosed = false,
                useEndTime = form.useEndTime,
                endTime = form.endTime,
                participantCount = 0,
                writeUserId = myId,
                multiSelect = form.multiSelect,
                allowAddItem = form.allowAddItem,
                items = items,
                voters = emptyList(),
            )
            save(myId, chatRoomId, data)
            // 생성 결과를 투표 대화 한 줄로 남긴다.
            appendVoteChat(myId, chatRoomId, data)
            Log.message("[Vote/Local] created $voteId in $chatRoomId (${items.size} items)")
            Result.success(voteId)
        }

    override suspend fun submitVote(
        chatRoomId: String,
        voteId: String,
        selectedIdx: Set<Int>,
    ): VoteData? = withContext(Dispatchers.Default) {
        val myId = Config.MyInfo.userId
        val current = load(chatRoomId, voteId) ?: return@withContext null
        if (current.isClosed) return@withContext current

        // 기존 표를 걷어낸 뒤 새로 선택한 항목에 넣는다.
        val cleared = current.withoutVoter(myId)
        val myName = TestAccounts.find(myId)?.userName ?: myId
        val updated = cleared.copy(
            voters = cleared.voters + selectedIdx.map {
                VoteVoter(itemIdx = it, userID = myId, userName = myName, voteDate = "")
            },
            items = cleared.items.map { item ->
                if (item.idx in selectedIdx) item.copy(nVote = item.nVote + 1) else item
            },
        ).withParticipantCount()

        save(myId, chatRoomId, updated)
        Log.message("[Vote/Local] submitted $voteId → ${selectedIdx.joinToString()}")
        updated
    }

    override suspend fun reVote(chatRoomId: String, voteId: String): VoteData? =
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            val current = load(chatRoomId, voteId) ?: return@withContext null
            val updated = current.withoutVoter(myId).withParticipantCount()
            save(myId, chatRoomId, updated)
            updated
        }

    override suspend fun closeVote(chatRoomId: String, voteId: String, title: String) {
        withContext(Dispatchers.Default) {
            val myId = Config.MyInfo.userId
            val current = load(chatRoomId, voteId) ?: return@withContext
            val closed = current.copy(isClosed = true)
            save(myId, chatRoomId, closed)
            appendVoteCompleteChat(myId, chatRoomId, closed)
            Log.message("[Vote/Local] closed $voteId")
        }
    }

    // ---- 대화 리스트 반영 ----

    /** 투표 생성 대화([Chat.Type.VOTE]) 한 줄 추가. */
    private suspend fun appendVoteChat(myId: String, chatRoomId: String, data: VoteData) {
        val chat = baseChat(myId, chatRoomId, data, Chat.Type.VOTE).copy(
            vote = Vote(
                id = data.id,
                items = data.items.sortedBy { it.seq }.map { it.content },
                itemType = "TEXT",
                setting = VoteSetting(
                    settingEndTime = data.useEndTime,
                    endTime = data.endTime,
                    multiSelect = data.multiSelect,
                    allowAddItem = data.allowAddItem,
                ),
            )
        )
        insertChat(myId, chatRoomId, chat)
    }

    /** 투표 종료 대화([Chat.Type.VOTE_COMPLETE]) 한 줄 추가 — 항목별 득표 포함. */
    private suspend fun appendVoteCompleteChat(myId: String, chatRoomId: String, data: VoteData) {
        val chat = baseChat(myId, chatRoomId, data, Chat.Type.VOTE_COMPLETE).copy(
            voteComplete = VoteComplete(
                id = data.id,
                items = data.items.sortedBy { it.seq }.map {
                    VoteResultItem(
                        idx = it.idx,
                        seq = it.seq,
                        content = it.content,
                        nVote = it.nVote,
                        writeUserId = it.writeUserId,
                    )
                },
            )
        )
        insertChat(myId, chatRoomId, chat)
    }

    private fun baseChat(
        myId: String,
        chatRoomId: String,
        data: VoteData,
        type: String,
    ) = Chat.Item(
        chatID = ChatIdUtils.generateChatId(myId),
        chatType = type,
        // 투표 화면은 제목을 Chat.Item.title 에서 읽는다.
        title = data.title,
        chatStatue = Chat.Statue.COMPLETE,
        date = nowChatDate(),
        unReadCount = "0",
        user = User(id = myId, name = TestAccounts.find(myId)?.userName ?: myId),
    )

    /** 대화 저장소의 공통 경로로 넘긴다 — 저장·목록갱신·미리보기·하단 스크롤 신호 처리. */
    private suspend fun insertChat(myId: String, chatRoomId: String, chat: Chat.Item) {
        chatRoomRepository.appendMyChat(chatRoomId, chat)
    }

    /** `yyyy-MM-dd HH:mm:ss:SSS` — ChatEntity 가 쓰는 대화 시각 포맷. */
    private fun nowChatDate(): String {
        val dt = Instant.fromEpochMilliseconds(kotlin.time.Clock.System.now().toEpochMilliseconds())
            .toLocalDateTime(TimeZone.currentSystemDefault())
        fun p(v: Int, n: Int = 2) = v.toString().padStart(n, '0')
        return "${p(dt.year, 4)}-${p(dt.monthNumber)}-${p(dt.dayOfMonth)} " +
            "${p(dt.hour)}:${p(dt.minute)}:${p(dt.second)}:${p(dt.nanosecond / 1_000_000, 3)}"
    }

    // ---- 저장/조회 ----

    private fun load(chatRoomId: String, voteId: String): VoteData? {
        val myId = Config.MyInfo.userId
        if (myId.isBlank()) return null
        return dbQueries.selectVoteById(myId, chatRoomId, voteId).executeAsOneOrNull()?.toData()
    }

    private fun save(myId: String, chatRoomId: String, data: VoteData) {
        dbQueries.insertVote(
            VoteEntity(
                myId = myId,
                chatRoomId = chatRoomId,
                voteId = data.id,
                title = data.title,
                isClosed = data.isClosed.toDb(),
                useEndTime = data.useEndTime.toDb(),
                endTime = data.endTime,
                writeUserId = data.writeUserId,
                multiSelect = data.multiSelect.toDb(),
                allowAddItem = data.allowAddItem.toDb(),
                itemsJson = json.encodeToString(data.items.map { it.toRecord() }),
                votersJson = json.encodeToString(data.voters.map { it.toRecord() }),
            )
        )
    }

    private fun VoteEntity.toData(): VoteData {
        val items = decode<ItemRecord>(itemsJson).map { it.toModel() }
        val voters = decode<VoterRecord>(votersJson).map { it.toModel() }
        return VoteData(
            id = voteId,
            title = title,
            isClosed = isClosed.toBool(),
            useEndTime = useEndTime.toBool(),
            endTime = endTime.orEmpty(),
            participantCount = voters.map { it.userID }.distinct().size,
            writeUserId = writeUserId,
            multiSelect = multiSelect.toBool(),
            allowAddItem = allowAddItem.toBool(),
            items = items,
            voters = voters,
        )
    }

    private inline fun <reified T> decode(raw: String): List<T> =
        runCatching { json.decodeFromString<List<T>>(raw) }.getOrElse {
            Log.message("[Vote/Local] decode failed: ${it.message}")
            emptyList()
        }

    // ---- 저장 전용 형태 (도메인 모델에는 직렬화 애너테이션이 없다) ----

    @Serializable
    private data class ItemRecord(
        val idx: Int,
        val seq: Int,
        val content: String,
        val nVote: Int,
        val writeUserId: String,
    ) {
        fun toModel() = VoteDataItem(idx, seq, content, nVote, writeUserId)
    }

    @Serializable
    private data class VoterRecord(
        val itemIdx: Int,
        val userID: String,
        val userName: String,
        val voteDate: String,
    ) {
        fun toModel() = VoteVoter(itemIdx, userID, userName, voteDate)
    }

    private fun VoteDataItem.toRecord() = ItemRecord(idx, seq, content, nVote, writeUserId)
    private fun VoteVoter.toRecord() = VoterRecord(itemIdx, userID, userName, voteDate)
}

/** 기존 테이블(isRecalled 등)과 맞춘 'true'/'false' 문자열 표현. */
private fun Boolean.toDb(): String = if (this) "true" else "false"
private fun String?.toBool(): Boolean = this.equals("true", ignoreCase = true)

/** 내 표를 모두 제거 — 항목별 득표수도 함께 되돌린다. */
private fun VoteData.withoutVoter(myId: String): VoteData {
    val mine = voters.filter { it.userID.equals(myId, ignoreCase = true) }
    if (mine.isEmpty()) return this
    val myIdxSet = mine.map { it.itemIdx }.toSet()
    return copy(
        voters = voters.filterNot { it.userID.equals(myId, ignoreCase = true) },
        items = items.map { item ->
            if (item.idx in myIdxSet) item.copy(nVote = (item.nVote - 1).coerceAtLeast(0)) else item
        },
    )
}

private fun VoteData.withParticipantCount(): VoteData =
    copy(participantCount = voters.map { it.userID }.distinct().size)

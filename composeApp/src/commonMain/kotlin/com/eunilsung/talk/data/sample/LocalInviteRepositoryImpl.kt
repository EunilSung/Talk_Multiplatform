package com.eunilsung.talk.data.sample

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.eunilsung.talk.Config
import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.repository.ChatRoomListRepository
import com.eunilsung.talk.domain.repository.InviteRepository
import com.eunilsung.talk.util.ChatIdUtils
import com.eunilsung.talk.util.UserListCodec
import com.eunilsung.talk.util.Log

/** `ChatRoomEntity` 에 직접 쓰는 초대 / 대화방 생성 저장소. */
class LocalInviteRepositoryImpl(
    private val chatRoomListRepository: ChatRoomListRepository,
    private val mapper: ChatRoomMapper,
    database: AppDatabase,
) : InviteRepository {

    private val dbQueries = database.appDatabaseQueries

    override suspend fun inviteUsers(
        chatRoomId: String,
        invitedUsers: List<Pair<String, String>>,
        existingUserCount: Int,
    ): String? {
        val myId = Config.MyInfo.userId
        if (myId.isBlank()) return null

        val cleaned = invitedUsers
            .filter { it.first.isNotBlank() }
            .distinctBy { it.first }
        if (cleaned.isEmpty()) return null

        val roomId = withContext(Dispatchers.Default) {
            // blank / "-" 이면 신규 생성, 아니면 기존 방에 추가.
            val isNewRoom = chatRoomId.isBlank() || chatRoomId == "-"
            if (isNewRoom) createRoom(myId, cleaned) else addToRoom(myId, chatRoomId, cleaned)
        } ?: return null

        chatRoomListRepository.fetchChatRoomInfo(roomId)
        return roomId
    }

    /**
     * 신규 방 생성 — 단, 1:1 은 기존 방이 있으면 그 방을 돌려준다.
     *
     * 초대 목록에 내가 들어 있으면(나와의 대화방) 참여자에서 빼고 나를 한 번만 싣는다.
     */
    private fun createRoom(myId: String, invited: List<Pair<String, String>>): String {
        if (invited.size == 1) {
            val targetId = invited.first().first
            findDirectRoom(myId, targetId)?.let { existing ->
                Log.message("[Invite/Local] reuse existing 1:1 room ${existing} (with $targetId)")
                return existing
            }
        }

        val roomId = ChatIdUtils.generateChatId(myId)
        val members = listOf(myId to myName()) + invited.filterNot { it.first.equals(myId, ignoreCase = true) }

        val room = ChatRoom.Item(
            id = roomId,
            // 단체방 제목 — 참여자 이름을 이어붙인다.
            title = invited.joinToString(", ") { it.second.ifBlank { it.first } },
            unReadCount = "0",
            isAlarm = "0",
            lastChatDate = nowTimestamp(),
            lastChatContent = "",
            totalUserList = UserListCodec.encode(members),
            totalUserCount = members.size.toString(),
            exitUserList = "",
            exitUserCount = "0",
            pinDate = "",
            mentionCount = "0",
            enableMode = "0",
        )
        dbQueries.insertChatRoom(mapper.toEntity(myId, room))
        Log.message("[Invite/Local] created room $roomId with ${invited.size} user(s)")
        return roomId
    }

    /** 기존 방에 사용자 추가 — 이미 있는 참여자는 건너뛴다. */
    private fun addToRoom(
        myId: String,
        roomId: String,
        invited: List<Pair<String, String>>,
    ): String? {
        val entity = dbQueries.selectChatRoomsByMyId(myId).executeAsList()
            .firstOrNull { it.roomId == roomId }
            ?: run {
                Log.message("[Invite/Local] room not found: $roomId")
                return null
            }

        val current = mapper.toModel(entity)
        val existing = UserListCodec.decode(current.totalUserList)
        val existingIds = existing.map { it.first }.toSet()
        val added = invited.filterNot { it.first in existingIds }
        if (added.isEmpty()) {
            Log.message("[Invite/Local] all invitees already in room $roomId")
            return roomId
        }

        val members = existing + added
        val updated = current.copy(
            totalUserList = UserListCodec.encode(members),
            totalUserCount = members.size.toString(),
        )
        dbQueries.insertChatRoom(mapper.toEntity(myId, updated))
        Log.message("[Invite/Local] added ${added.size} user(s) to $roomId")
        return roomId
    }

    /** [targetId] 와의 기존 1:1 방 id. 없으면 null. */
    private fun findDirectRoom(myId: String, targetId: String): String? {
        val expectedCount = if (targetId.equals(myId, ignoreCase = true)) "1" else "2"
        return dbQueries.selectChatRoomsByMyId(myId).executeAsList()
            .map { mapper.toModel(it) }
            .firstOrNull { room ->
                room.totalUserCount == expectedCount &&
                    !room.id.contains("$") &&
                    UserListCodec.decode(room.totalUserList).any { it.first == targetId }
            }
            ?.id
    }

    private fun myName(): String =
        Config.MyInfo.userName.ifBlank { Config.MyInfo.userId }

    /** `yyyy-MM-dd HH:mm:ss` 포맷 현재 시각. */
    private fun nowTimestamp(): String {
        val dt = kotlinx.datetime.Instant
            .fromEpochMilliseconds(kotlin.time.Clock.System.now().toEpochMilliseconds())
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val y = dt.year.toString().padStart(4, '0')
        val mo = dt.monthNumber.toString().padStart(2, '0')
        val d = dt.dayOfMonth.toString().padStart(2, '0')
        val h = dt.hour.toString().padStart(2, '0')
        val mi = dt.minute.toString().padStart(2, '0')
        val s = dt.second.toString().padStart(2, '0')
        return "$y-$mo-$d $h:$mi:$s"
    }
}

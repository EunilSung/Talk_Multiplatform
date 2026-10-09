package com.eunilsung.talk.server.repository

import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.RoomDto
import com.eunilsung.talk.shared.api.RoomMemberDto
import com.eunilsung.talk.shared.api.UnreadCountDto
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

/** 방을 만들거나 참여자가 바뀐 결과. [message] 는 그 일을 알리는 시스템 대화(없을 수도 있다). */
data class RoomChange(
    val roomId: String,
    val message: MessageDto? = null,
)

/**
 * 대화방·대화 저장소.
 *
 * 모든 조회와 변경은 "누가 요청했는가"를 함께 받는다. 참여 중이 아닌 방은 없는 방과 똑같이 null 을
 * 돌려준다 — 호출하는 쪽이 권한 확인을 빠뜨려도 남의 방이 새지 않는다.
 *
 * 방 안의 순서를 바꾸는 일(대화 넣기, 초대, 퇴장)은 방 행을 잠그고 한다. 그래야 번호가 건너뛰거나
 * 겹치지 않고, 같은 대화를 두 번 보내도 한 번만 들어간다.
 */
class ChatRepository(private val dataSource: DataSource) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** 내가 참여 중인 방 전부. */
    fun rooms(userId: String): List<RoomDto> =
        dataSource.connection.use { conn -> selectRooms(conn, userId, roomId = null) }

    /** 방 하나. 참여 중이 아니면 null. */
    fun room(roomId: String, userId: String): RoomDto? =
        dataSource.connection.use { conn -> selectRooms(conn, userId, roomId).firstOrNull() }

    /** 지금 참여 중인 사람들 — 알림을 보낼 대상이다. */
    fun activeMemberIds(roomId: String): List<String> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT user_id FROM chat_room_member WHERE room_id = ? AND left_at IS NULL"
            ).use { st ->
                st.setString(1, roomId)
                st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            }
        }

    /**
     * 방을 만든다. 모르는 아이디가 섞여 있으면 null.
     *
     * 상대가 한 명 이하이면(1:1 또는 나와의 대화) 이미 있는 방을 돌려준다. 그 방에서 누가 나가 있었으면
     * 다시 들어오게 하되, 나가 있던 동안의 대화는 보이지 않게 한다.
     */
    fun createRoom(creatorId: String, memberIds: List<String>): RoomChange? = transaction { conn ->
        val ids = (listOf(creatorId) + memberIds.map(::normalize)).distinct()
        val names = userNames(conn, ids)
        if (names.size != ids.size) return@transaction null

        if (ids.size <= DIRECT_ROOM_MAX_MEMBERS) {
            RoomChange(openDirectRoom(conn, ids))
        } else {
            val roomId = UUID.randomUUID().toString()
            conn.prepareStatement("INSERT INTO chat_room (id) VALUES (?)").use { st ->
                st.setString(1, roomId)
                st.executeUpdate()
            }
            ids.forEach { addMember(conn, roomId, it, joinedSeq = 0) }
            val invited = ids.filterNot { it == creatorId }.mapNotNull(names::get)
            RoomChange(roomId, insertSystemMessage(conn, roomId, creatorId, MessageKind.INVITE, invited))
        }
    }

    /**
     * 방에 사람을 더 부른다. 방이 없거나 내가 참여자가 아니거나 모르는 아이디가 있으면 null.
     *
     * 새로 들어온 사람은 초대 알림부터 본다. 이미 있는 사람만 골랐으면 아무 일도 하지 않는다.
     */
    fun invite(roomId: String, actorId: String, userIds: List<String>): RoomChange? = transaction { conn ->
        val lastSeq = lockRoom(conn, roomId) ?: return@transaction null
        if (!isActiveMember(conn, roomId, actorId)) return@transaction null
        val ids = userIds.map(::normalize).distinct()
        val names = userNames(conn, ids)
        if (names.size != ids.size) return@transaction null

        val added = ids.filter { addMember(conn, roomId, it, joinedSeq = lastSeq) }
        if (added.isEmpty()) return@transaction RoomChange(roomId)

        /** 사람이 늘면 더 이상 1:1 방이 아니다. 표시를 지워야 그 둘이 새 1:1 방을 열 수 있다. */
        conn.prepareStatement("UPDATE chat_room SET direct_key = NULL WHERE id = ?").use { st ->
            st.setString(1, roomId)
            st.executeUpdate()
        }
        RoomChange(roomId, insertSystemMessage(conn, roomId, actorId, MessageKind.INVITE, added.mapNotNull(names::get)))
    }

    /** 방에서 나간다. 남은 사람들에게 퇴장 알림이 남는다. 참여 중이 아니었으면 null. */
    fun leave(roomId: String, userId: String): RoomChange? = transaction { conn ->
        lockRoom(conn, roomId) ?: return@transaction null
        if (!isActiveMember(conn, roomId, userId)) return@transaction null

        val message = insertSystemMessage(conn, roomId, userId, MessageKind.EXIT, emptyList())
        conn.prepareStatement(
            "UPDATE chat_room_member SET left_at = now() WHERE room_id = ? AND user_id = ?"
        ).use { st ->
            st.setString(1, roomId)
            st.setString(2, userId)
            st.executeUpdate()
        }
        RoomChange(roomId, message)
    }

    /** 방 이름을 바꾼다. 참여자 모두에게 바뀐다. 참여 중이 아니면 false. */
    fun rename(roomId: String, userId: String, title: String): Boolean = transaction { conn ->
        if (!isActiveMember(conn, roomId, userId)) return@transaction false
        conn.prepareStatement("UPDATE chat_room SET title = ? WHERE id = ?").use { st ->
            st.setString(1, title)
            st.setString(2, roomId)
            st.executeUpdate() > 0
        }
    }

    /** 이 방의 알림을 끄거나 켠다. 나에게만 적용된다. */
    fun setMuted(roomId: String, userId: String, isMuted: Boolean): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE chat_room_member SET is_muted = ? WHERE room_id = ? AND user_id = ? AND left_at IS NULL"
            ).use { st ->
                st.setBoolean(1, isMuted)
                st.setString(2, roomId)
                st.setString(3, userId)
                st.executeUpdate() > 0
            }
        }

    /**
     * 대화를 넣는다. 참여 중이 아니면 null.
     *
     * 같은 [clientId] 가 이미 있으면 새로 넣지 않고 있던 것을 돌려준다. 앱은 응답을 못 받으면 같은 id 로
     * 다시 보내는데, 그때 대화가 두 번 생기면 안 된다.
     */
    fun send(
        roomId: String,
        senderId: String,
        clientId: String,
        kind: String,
        content: String,
        payload: MessagePayloadDto?,
    ): MessageDto? = transaction { conn ->
        lockRoom(conn, roomId) ?: return@transaction null
        if (!isActiveMember(conn, roomId, senderId)) return@transaction null
        seqOf(conn, roomId, clientId)?.let { return@transaction selectMessage(conn, roomId, it) }
        insertMessage(conn, roomId, senderId, clientId, kind, content, payload)
    }

    /**
     * 대화 한 쪽을 seq 오름차순으로 돌려준다. 참여 중이 아니면 null.
     *
     * [afterId] 를 주면 그 뒤의 대화를 앞에서부터, [beforeId] 를 주면 그 앞의 대화를 뒤에서부터,
     * 둘 다 없으면 가장 최근 대화를 준다. 내가 들어오기 전의 대화는 어느 경우에도 나오지 않는다.
     */
    fun messages(
        roomId: String,
        userId: String,
        afterId: String?,
        beforeId: String?,
        limit: Int,
    ): List<MessageDto>? = dataSource.connection.use { conn ->
        val joinedSeq = joinedSeq(conn, roomId, userId) ?: return@use null
        val afterSeq = afterId?.let { seqOf(conn, roomId, it) }
        val beforeSeq = beforeId?.let { seqOf(conn, roomId, it) }

        val lower = maxOf(joinedSeq, afterSeq ?: 0)
        val upper = beforeSeq ?: Long.MAX_VALUE
        val fromOldest = afterSeq != null
        conn.prepareStatement(
            "$MESSAGE_SELECT WHERE g.room_id = ? AND g.seq > ? AND g.seq < ? " +
                "ORDER BY g.seq ${if (fromOldest) "ASC" else "DESC"} LIMIT ?"
        ).use { st ->
            st.setString(1, roomId)
            st.setLong(2, lower)
            st.setLong(3, upper)
            st.setInt(4, limit)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toMessage()) } }
        }.sortedBy { it.seq }
    }

    /**
     * [messageId] 까지 읽었다고 적는다. 읽은 자리가 실제로 앞으로 갔으면 true.
     *
     * 더 앞의 대화를 가리키면 아무 일도 하지 않는다. 늦게 도착한 요청이 읽은 자리를 되돌리면
     * 이미 읽은 대화가 다시 안읽음이 된다.
     */
    fun markRead(roomId: String, userId: String, messageId: String): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE chat_room_member m SET last_read_seq = g.seq
                FROM chat_message g
                WHERE m.room_id = ? AND m.user_id = ? AND m.left_at IS NULL
                  AND g.room_id = m.room_id AND g.client_id = ? AND g.seq > m.last_read_seq
                """.trimIndent()
            ).use { st ->
                st.setString(1, roomId)
                st.setString(2, userId)
                st.setString(3, messageId)
                st.executeUpdate() > 0
            }
        }

    /** [fromId] 부터 뒤쪽 대화들의 안읽음 수. 참여 중이 아니면 null. */
    fun unreadCounts(roomId: String, userId: String, fromId: String): List<UnreadCountDto>? =
        dataSource.connection.use { conn ->
            val joinedSeq = joinedSeq(conn, roomId, userId) ?: return@use null
            val fromSeq = seqOf(conn, roomId, fromId) ?: return@use emptyList()
            conn.prepareStatement(
                """
                SELECT g.client_id, $UNREAD_COUNT AS unread
                FROM chat_message g
                WHERE g.room_id = ? AND g.seq >= ? AND g.seq > ?
                ORDER BY g.seq
                """.trimIndent()
            ).use { st ->
                st.setString(1, roomId)
                st.setLong(2, fromSeq)
                st.setLong(3, joinedSeq)
                st.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(UnreadCountDto(rs.getString(1), rs.getInt(2))) }
                }
            }
        }

    private fun selectRooms(conn: Connection, userId: String, roomId: String?): List<RoomDto> {
        data class Row(val id: String, val title: String, val lastSeq: Long, val readSeq: Long, val joinedSeq: Long, val isMuted: Boolean, val createdAt: Long)

        val rows = conn.prepareStatement(
            """
            SELECT r.id, r.title, r.next_seq - 1 AS last_seq, m.last_read_seq, m.joined_seq, m.is_muted, r.created_at
            FROM chat_room r JOIN chat_room_member m ON m.room_id = r.id
            WHERE m.user_id = ? AND m.left_at IS NULL ${if (roomId != null) "AND r.id = ?" else ""}
            """.trimIndent()
        ).use { st ->
            st.setString(1, userId)
            if (roomId != null) st.setString(2, roomId)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(Row(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getBoolean(6), rs.getTimestamp(7).time))
                    }
                }
            }
        }

        return rows.map { row ->
            RoomDto(
                id = row.id,
                title = row.title,
                members = selectMembers(conn, row.id),
                lastMessage = selectLastMessage(conn, row.id, row.joinedSeq),
                unreadCount = (row.lastSeq - maxOf(row.readSeq, row.joinedSeq)).coerceAtLeast(0).toInt(),
                isMuted = row.isMuted,
                createdAtEpochMillis = row.createdAt,
            )
        }
    }

    private fun selectMembers(conn: Connection, roomId: String): List<RoomMemberDto> =
        conn.prepareStatement(
            """
            SELECT m.user_id, u.name, m.left_at IS NOT NULL
            FROM chat_room_member m JOIN app_user u ON u.id = m.user_id
            WHERE m.room_id = ?
            ORDER BY u.position_sort, u.name
            """.trimIndent()
        ).use { st ->
            st.setString(1, roomId)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(RoomMemberDto(rs.getString(1), rs.getString(2), rs.getBoolean(3))) }
            }
        }

    private fun selectLastMessage(conn: Connection, roomId: String, joinedSeq: Long): MessageDto? =
        conn.prepareStatement(
            "$MESSAGE_SELECT WHERE g.room_id = ? AND g.seq > ? ORDER BY g.seq DESC LIMIT 1"
        ).use { st ->
            st.setString(1, roomId)
            st.setLong(2, joinedSeq)
            st.executeQuery().use { rs -> if (rs.next()) rs.toMessage() else null }
        }

    private fun selectMessage(conn: Connection, roomId: String, seq: Long): MessageDto? =
        conn.prepareStatement("$MESSAGE_SELECT WHERE g.room_id = ? AND g.seq = ?").use { st ->
            st.setString(1, roomId)
            st.setLong(2, seq)
            st.executeQuery().use { rs -> if (rs.next()) rs.toMessage() else null }
        }

    /** 방 행을 잠그고 지금까지의 마지막 번호를 돌려준다. 방이 없으면 null. */
    private fun lockRoom(conn: Connection, roomId: String): Long? =
        conn.prepareStatement("SELECT next_seq - 1 FROM chat_room WHERE id = ? FOR UPDATE").use { st ->
            st.setString(1, roomId)
            st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
        }

    /** 1:1 방(또는 나와의 대화방)을 찾거나 만든다. */
    private fun openDirectRoom(conn: Connection, ids: List<String>): String {
        val directKey = ids.sorted().joinToString(DIRECT_KEY_SEPARATOR)
        conn.prepareStatement(
            "INSERT INTO chat_room (id, direct_key) VALUES (?, ?) ON CONFLICT (direct_key) DO NOTHING"
        ).use { st ->
            st.setString(1, UUID.randomUUID().toString())
            st.setString(2, directKey)
            st.executeUpdate()
        }
        val (roomId, lastSeq) = conn.prepareStatement(
            "SELECT id, next_seq - 1 FROM chat_room WHERE direct_key = ? FOR UPDATE"
        ).use { st ->
            st.setString(1, directKey)
            st.executeQuery().use { rs ->
                rs.next()
                rs.getString(1) to rs.getLong(2)
            }
        }
        ids.forEach { addMember(conn, roomId, it, joinedSeq = lastSeq) }
        return roomId
    }

    /**
     * 참여자를 넣는다. 나갔던 사람이면 다시 들어오게 한다. 실제로 들어왔으면 true, 이미 있었으면 false.
     *
     * [joinedSeq] 까지는 본 것으로 친다 — 들어오기 전의 대화가 안읽음으로 잡히지 않는다.
     */
    private fun addMember(conn: Connection, roomId: String, userId: String, joinedSeq: Long): Boolean =
        conn.prepareStatement(
            """
            INSERT INTO chat_room_member (room_id, user_id, joined_seq, last_read_seq) VALUES (?, ?, ?, ?)
            ON CONFLICT (room_id, user_id) DO UPDATE
                SET left_at = NULL, joined_seq = EXCLUDED.joined_seq, last_read_seq = EXCLUDED.last_read_seq
                WHERE chat_room_member.left_at IS NOT NULL
            """.trimIndent()
        ).use { st ->
            st.setString(1, roomId)
            st.setString(2, userId)
            st.setLong(3, joinedSeq)
            st.setLong(4, joinedSeq)
            st.executeUpdate() > 0
        }

    private fun insertSystemMessage(
        conn: Connection,
        roomId: String,
        actorId: String,
        kind: String,
        targetNames: List<String>,
    ): MessageDto = insertMessage(
        conn = conn,
        roomId = roomId,
        senderId = actorId,
        clientId = UUID.randomUUID().toString(),
        kind = kind,
        content = "",
        payload = MessagePayloadDto(targetNames = targetNames).takeIf { targetNames.isNotEmpty() },
    )

    /** 번호를 뽑아 대화를 넣고, 보낸 사람은 거기까지 읽은 것으로 적는다. 방 행을 잠근 채로 불러야 한다. */
    private fun insertMessage(
        conn: Connection,
        roomId: String,
        senderId: String,
        clientId: String,
        kind: String,
        content: String,
        payload: MessagePayloadDto?,
    ): MessageDto {
        val seq = conn.prepareStatement(
            "UPDATE chat_room SET next_seq = next_seq + 1 WHERE id = ? RETURNING next_seq - 1"
        ).use { st ->
            st.setString(1, roomId)
            st.executeQuery().use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
        conn.prepareStatement(
            "INSERT INTO chat_message (room_id, seq, client_id, sender_id, kind, content, payload) VALUES (?, ?, ?, ?, ?, ?, ?)"
        ).use { st ->
            st.setString(1, roomId)
            st.setLong(2, seq)
            st.setString(3, clientId)
            st.setString(4, senderId)
            st.setString(5, kind)
            st.setString(6, content)
            st.setString(7, payload?.let { json.encodeToString(MessagePayloadDto.serializer(), it) })
            st.executeUpdate()
        }
        conn.prepareStatement(
            "UPDATE chat_room_member SET last_read_seq = GREATEST(last_read_seq, ?) WHERE room_id = ? AND user_id = ?"
        ).use { st ->
            st.setLong(1, seq)
            st.setString(2, roomId)
            st.setString(3, senderId)
            st.executeUpdate()
        }
        return checkNotNull(selectMessage(conn, roomId, seq))
    }

    private fun isActiveMember(conn: Connection, roomId: String, userId: String): Boolean =
        joinedSeq(conn, roomId, userId) != null

    /** 참여 중이면 어디서부터 볼 수 있는지, 아니면 null. */
    private fun joinedSeq(conn: Connection, roomId: String, userId: String): Long? =
        conn.prepareStatement(
            "SELECT joined_seq FROM chat_room_member WHERE room_id = ? AND user_id = ? AND left_at IS NULL"
        ).use { st ->
            st.setString(1, roomId)
            st.setString(2, userId)
            st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
        }

    private fun seqOf(conn: Connection, roomId: String, clientId: String): Long? =
        conn.prepareStatement("SELECT seq FROM chat_message WHERE room_id = ? AND client_id = ?").use { st ->
            st.setString(1, roomId)
            st.setString(2, clientId)
            st.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
        }

    /** 아이디 → 이름. 없는 아이디는 빠진다. */
    private fun userNames(conn: Connection, ids: List<String>): Map<String, String> =
        conn.prepareStatement("SELECT id, name FROM app_user WHERE id = ANY (?)").use { st ->
            st.setArray(1, conn.createArrayOf("text", ids.toTypedArray()))
            st.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getString(2)) } }
        }

    private fun ResultSet.toMessage() = MessageDto(
        roomId = getString("room_id"),
        seq = getLong("seq"),
        id = getString("client_id"),
        senderId = getString("sender_id"),
        senderName = getString("sender_name"),
        kind = getString("kind"),
        content = getString("content"),
        payload = getString("payload")?.let { json.decodeFromString(MessagePayloadDto.serializer(), it) },
        sentAtEpochMillis = getTimestamp("sent_at").time,
        unreadCount = getInt("unread"),
    )

    private fun <T> transaction(block: (Connection) -> T): T =
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                block(conn).also { conn.commit() }
            } catch (cause: Throwable) {
                conn.rollback()
                throw cause
            } finally {
                conn.autoCommit = true
            }
        }

    private fun normalize(userId: String): String = userId.trim().lowercase()

    private companion object {
        /** 참여자가 이 수 이하이면 1:1 방(둘) 또는 나와의 대화방(하나)이다. */
        const val DIRECT_ROOM_MAX_MEMBERS = 2
        const val DIRECT_KEY_SEPARATOR = "|"

        /** 이 대화를 아직 읽지 않은 참여자 수. 나간 사람은 세지 않는다. */
        const val UNREAD_COUNT =
            "(SELECT count(*) FROM chat_room_member mm " +
                "WHERE mm.room_id = g.room_id AND mm.left_at IS NULL AND mm.last_read_seq < g.seq)"

        const val MESSAGE_SELECT =
            "SELECT g.room_id, g.seq, g.client_id, g.sender_id, u.name AS sender_name, g.kind, g.content, " +
                "g.payload, g.sent_at, $UNREAD_COUNT AS unread " +
                "FROM chat_message g JOIN app_user u ON u.id = g.sender_id"
    }
}

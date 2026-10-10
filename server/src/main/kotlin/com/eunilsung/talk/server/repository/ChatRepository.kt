package com.eunilsung.talk.server.repository

import com.eunilsung.talk.shared.api.BookmarkDto
import com.eunilsung.talk.shared.api.MessageDto
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.NoticeAction
import com.eunilsung.talk.shared.api.NoticeChangeResponse
import com.eunilsung.talk.shared.api.NoticeDto
import com.eunilsung.talk.shared.api.ReactionDto
import com.eunilsung.talk.shared.api.RoomDto
import com.eunilsung.talk.shared.api.RoomMemberDto
import com.eunilsung.talk.shared.api.UnreadCountDto
import kotlinx.serialization.builtins.ListSerializer
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
            "UPDATE chat_room_member SET left_at = now(), pinned_at = NULL WHERE room_id = ? AND user_id = ?"
        ).use { st ->
            st.setString(1, roomId)
            st.setString(2, userId)
            st.executeUpdate()
        }
        /** 나간 방은 내 대화그룹에서도 뺀다. 남겨 두면 다시 초대받았을 때 예전 그룹에 저절로 들어가 있다. */
        conn.prepareStatement("DELETE FROM chat_group_room WHERE user_id = ? AND room_id = ?").use { st ->
            st.setString(1, userId)
            st.setString(2, roomId)
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

    /** 이 방을 상단에 고정하거나 푼다. 나에게만 적용된다. 이미 고정한 방을 다시 고정해도 시각은 그대로다. */
    fun setPinned(roomId: String, userId: String, isPinned: Boolean): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE chat_room_member
                SET pinned_at = CASE WHEN ? THEN coalesce(pinned_at, now()) ELSE NULL END
                WHERE room_id = ? AND user_id = ? AND left_at IS NULL
                """.trimIndent()
            ).use { st ->
                st.setBoolean(1, isPinned)
                st.setString(2, roomId)
                st.setString(3, userId)
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
            .also { insertMentions(conn, roomId, it.seq, senderId, content) }
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

    /**
     * 공감을 누른다. 같은 종류를 다시 누르면 끄고, 다른 종류면 갈아탄다. 바뀐 대화를 돌려준다.
     *
     * 방에 없거나, 내가 볼 수 없는 대화이거나, 회수된 대화이거나, 모르는 종류이면 null.
     */
    fun toggleReaction(roomId: String, userId: String, messageId: String, kind: String): MessageDto? =
        transaction { conn ->
            if (kind !in REACTION_KINDS) return@transaction null
            lockRoom(conn, roomId) ?: return@transaction null
            val seq = visibleSeq(conn, roomId, userId, messageId) ?: return@transaction null
            if (selectMessage(conn, roomId, seq)?.isRecalled != false) return@transaction null

            val removed = conn.prepareStatement(
                "DELETE FROM chat_reaction WHERE room_id = ? AND seq = ? AND user_id = ? AND kind = ?"
            ).use { st ->
                st.setString(1, roomId)
                st.setLong(2, seq)
                st.setString(3, userId)
                st.setString(4, kind)
                st.executeUpdate() > 0
            }
            if (!removed) {
                conn.prepareStatement(
                    """
                    INSERT INTO chat_reaction (room_id, seq, user_id, kind) VALUES (?, ?, ?, ?)
                    ON CONFLICT (room_id, seq, user_id) DO UPDATE SET kind = EXCLUDED.kind, created_at = now()
                    """.trimIndent()
                ).use { st ->
                    st.setString(1, roomId)
                    st.setLong(2, seq)
                    st.setString(3, userId)
                    st.setString(4, kind)
                    st.executeUpdate()
                }
            }
            selectMessage(conn, roomId, seq)
        }

    /**
     * 내가 보낸 대화를 회수한다. 바뀐 대화를 돌려준다. 이미 회수했으면 그대로 돌려준다.
     *
     * 남의 대화이거나 초대·퇴장 같은 알림이면 null — 보낸 사람만 자기 말을 거둘 수 있다.
     */
    fun recall(roomId: String, userId: String, messageId: String): MessageDto? = transaction { conn ->
        if (!isActiveMember(conn, roomId, userId)) return@transaction null
        val seq = seqOf(conn, roomId, messageId) ?: return@transaction null
        conn.prepareStatement(
            """
            UPDATE chat_message SET recalled_at = coalesce(recalled_at, now())
            WHERE room_id = ? AND seq = ? AND sender_id = ? AND kind = ANY (?)
            """.trimIndent()
        ).use { st ->
            st.setString(1, roomId)
            st.setLong(2, seq)
            st.setString(3, userId)
            st.setArray(4, conn.createArrayOf("text", MessageKind.SENDABLE.toTypedArray()))
            if (st.executeUpdate() == 0) return@transaction null
        }
        selectMessage(conn, roomId, seq)
    }

    /** 이 대화에 딸린 파일의 id. 파일 대화가 아니면 null. */
    fun fileIdOf(roomId: String, messageId: String): String? =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT payload FROM chat_message WHERE room_id = ? AND client_id = ?").use { st ->
                st.setString(1, roomId)
                st.setString(2, messageId)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    rs.getString(1)?.let { json.decodeFromString(MessagePayloadDto.serializer(), it).fileId }
                }
            }
        }

    /** 방의 공지. 공지가 없으면 내용이 빈 값을 돌려준다. 참여 중이 아니면 null. */
    fun notice(roomId: String, userId: String): NoticeDto? = dataSource.connection.use { conn ->
        if (!isActiveMember(conn, roomId, userId)) return@use null
        selectNotice(conn, roomId) ?: NoticeDto(roomId = roomId)
    }

    /** 공지를 건다. 이미 있으면 바꾼다. 방에 공지 등록 알림이 남는다. 참여 중이 아니면 null. */
    fun setNotice(roomId: String, userId: String, content: String): NoticeChangeResponse? = transaction { conn ->
        lockRoom(conn, roomId) ?: return@transaction null
        if (!isActiveMember(conn, roomId, userId)) return@transaction null
        conn.prepareStatement(
            """
            INSERT INTO chat_notice (room_id, id, content, owner_id) VALUES (?, ?, ?, ?)
            ON CONFLICT (room_id) DO UPDATE
                SET id = EXCLUDED.id, content = EXCLUDED.content, owner_id = EXCLUDED.owner_id, created_at = now()
            """.trimIndent()
        ).use { st ->
            st.setString(1, roomId)
            st.setString(2, UUID.randomUUID().toString())
            st.setString(3, content)
            st.setString(4, userId)
            st.executeUpdate()
        }
        NoticeChangeResponse(
            notice = checkNotNull(selectNotice(conn, roomId)),
            message = insertNoticeMessage(conn, roomId, userId, content, NoticeAction.ADD),
        )
    }

    /** 공지를 내린다. 방에 공지 삭제 알림이 남는다. 참여 중이 아니거나 공지가 없으면 null. */
    fun deleteNotice(roomId: String, userId: String): NoticeChangeResponse? = transaction { conn ->
        lockRoom(conn, roomId) ?: return@transaction null
        if (!isActiveMember(conn, roomId, userId)) return@transaction null
        val previous = selectNotice(conn, roomId) ?: return@transaction null
        conn.prepareStatement("DELETE FROM chat_notice WHERE room_id = ?").use { st ->
            st.setString(1, roomId)
            st.executeUpdate()
        }
        NoticeChangeResponse(
            notice = NoticeDto(roomId = roomId),
            message = insertNoticeMessage(conn, roomId, userId, previous.content, NoticeAction.DELETE),
        )
    }

    /** 이 방에서 내가 꽂은 책갈피, 최근 대화부터. 참여 중이 아니면 null. */
    fun bookmarks(roomId: String, userId: String): List<BookmarkDto>? = dataSource.connection.use { conn ->
        if (!isActiveMember(conn, roomId, userId)) return@use null
        conn.prepareStatement(
            """
            SELECT g.client_id, CASE WHEN g.recalled_at IS NULL THEN g.content ELSE '' END, g.sender_id, u.name, g.sent_at
            FROM chat_bookmark b
            JOIN chat_message g ON g.room_id = b.room_id AND g.seq = b.seq
            JOIN app_user u ON u.id = g.sender_id
            WHERE b.user_id = ? AND b.room_id = ?
            ORDER BY b.seq DESC
            """.trimIndent()
        ).use { st ->
            st.setString(1, userId)
            st.setString(2, roomId)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(BookmarkDto(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).time))
                    }
                }
            }
        }
    }

    /** 책갈피를 꽂는다. 이미 꽂혀 있어도 성공이다. 내가 볼 수 없는 대화면 false. */
    fun addBookmark(roomId: String, userId: String, messageId: String): Boolean = dataSource.connection.use { conn ->
        val seq = visibleSeq(conn, roomId, userId, messageId) ?: return@use false
        conn.prepareStatement(
            "INSERT INTO chat_bookmark (user_id, room_id, seq) VALUES (?, ?, ?) ON CONFLICT DO NOTHING"
        ).use { st ->
            st.setString(1, userId)
            st.setString(2, roomId)
            st.setLong(3, seq)
            st.executeUpdate()
        }
        true
    }

    /** 책갈피를 뺀다. 꽂혀 있지 않았어도 성공이다. 참여 중이 아니면 false. */
    fun removeBookmark(roomId: String, userId: String, messageId: String): Boolean = dataSource.connection.use { conn ->
        if (!isActiveMember(conn, roomId, userId)) return@use false
        conn.prepareStatement(
            """
            DELETE FROM chat_bookmark b USING chat_message g
            WHERE b.user_id = ? AND b.room_id = ? AND g.room_id = b.room_id AND g.seq = b.seq AND g.client_id = ?
            """.trimIndent()
        ).use { st ->
            st.setString(1, userId)
            st.setString(2, roomId)
            st.setString(3, messageId)
            st.executeUpdate()
        }
        true
    }

    /** 내가 볼 수 있는 대화의 번호. 방에 없거나, 없는 대화이거나, 내가 들어오기 전 대화이면 null. */
    private fun visibleSeq(conn: Connection, roomId: String, userId: String, messageId: String): Long? {
        val joinedSeq = joinedSeq(conn, roomId, userId) ?: return null
        return seqOf(conn, roomId, messageId)?.takeIf { it > joinedSeq }
    }

    private fun selectNotice(conn: Connection, roomId: String): NoticeDto? =
        conn.prepareStatement(
            """
            SELECT n.id, n.content, n.owner_id, u.name, u.position_name, n.created_at
            FROM chat_notice n JOIN app_user u ON u.id = n.owner_id
            WHERE n.room_id = ?
            """.trimIndent()
        ).use { st ->
            st.setString(1, roomId)
            st.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                NoticeDto(
                    roomId = roomId,
                    id = rs.getString(1),
                    content = rs.getString(2),
                    ownerId = rs.getString(3),
                    ownerName = rs.getString(4),
                    ownerPositionName = rs.getString(5),
                    createdAtEpochMillis = rs.getTimestamp(6).time,
                )
            }
        }

    private fun insertNoticeMessage(
        conn: Connection,
        roomId: String,
        actorId: String,
        content: String,
        action: String,
    ): MessageDto = insertMessage(
        conn = conn,
        roomId = roomId,
        senderId = actorId,
        clientId = UUID.randomUUID().toString(),
        kind = MessageKind.NOTICE,
        content = content,
        payload = MessagePayloadDto(noticeAction = action),
    )

    private fun selectRooms(conn: Connection, userId: String, roomId: String?): List<RoomDto> {
        data class Row(val id: String, val title: String, val lastSeq: Long, val readSeq: Long, val joinedSeq: Long, val isMuted: Boolean, val createdAt: Long, val mentions: Int, val pinnedAt: Long)

        val rows = conn.prepareStatement(
            """
            SELECT r.id, r.title, r.next_seq - 1 AS last_seq, m.last_read_seq, m.joined_seq, m.is_muted, r.created_at,
                   coalesce(extract(epoch FROM m.pinned_at) * 1000, 0)::bigint AS pinned_at,
                   (SELECT count(*) FROM chat_mention t
                    WHERE t.room_id = r.id AND t.user_id = m.user_id AND t.seq > m.last_read_seq) AS mentions
            FROM chat_room r JOIN chat_room_member m ON m.room_id = r.id
            WHERE m.user_id = ? AND m.left_at IS NULL ${if (roomId != null) "AND r.id = ?" else ""}
            """.trimIndent()
        ).use { st ->
            st.setString(1, userId)
            if (roomId != null) st.setString(2, roomId)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(Row(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getBoolean(6), rs.getTimestamp(7).time, rs.getInt(9), rs.getLong(8)))
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
                mentionCount = row.mentions,
                pinnedAtEpochMillis = row.pinnedAt,
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
    internal fun lockRoom(conn: Connection, roomId: String): Long? =
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
    internal fun insertMessage(
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

    /**
     * 본문의 멘션 태그가 부른 사람들을 적어 둔다.
     *
     * 태그에는 이름만 들어 있다(`<mention>@이름</mention>`). 이 방에 참여 중인 사람 가운데 그 이름인
     * 사람을 찾는다. 이름이 같은 사람이 둘이면 둘 다 불린 것으로 본다. 자기 자신은 세지 않는다.
     */
    private fun insertMentions(conn: Connection, roomId: String, seq: Long, senderId: String, content: String) {
        val names = MENTION_TAG.findAll(content).map { it.groupValues[1].trim().removePrefix("@") }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (names.isEmpty()) return
        conn.prepareStatement(
            """
            INSERT INTO chat_mention (room_id, seq, user_id)
            SELECT m.room_id, ?, m.user_id
            FROM chat_room_member m JOIN app_user u ON u.id = m.user_id
            WHERE m.room_id = ? AND m.left_at IS NULL AND m.user_id <> ? AND u.name = ANY (?)
            ON CONFLICT DO NOTHING
            """.trimIndent()
        ).use { st ->
            st.setLong(1, seq)
            st.setString(2, roomId)
            st.setString(3, senderId)
            st.setArray(4, conn.createArrayOf("text", names.toTypedArray()))
            st.executeUpdate()
        }
    }

    internal fun isActiveMember(conn: Connection, roomId: String, userId: String): Boolean =
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

    /** 회수된 대화는 본문·부가 정보·공감을 비워서 내보낸다. DB 에는 남아 있어도 밖으로 나가지 않는다. */
    private fun ResultSet.toMessage(): MessageDto {
        val isRecalled = getBoolean("recalled")
        return toMessage(isRecalled)
    }

    private fun ResultSet.toMessage(isRecalled: Boolean) = MessageDto(
        roomId = getString("room_id"),
        seq = getLong("seq"),
        id = getString("client_id"),
        senderId = getString("sender_id"),
        senderName = getString("sender_name"),
        kind = getString("kind"),
        content = if (isRecalled) "" else getString("content"),
        payload = getString("payload")?.takeUnless { isRecalled }
            ?.let { json.decodeFromString(MessagePayloadDto.serializer(), it) },
        sentAtEpochMillis = getTimestamp("sent_at").time,
        unreadCount = getInt("unread"),
        isRecalled = isRecalled,
        reactions = if (isRecalled) emptyList() else json.decodeFromString(REACTIONS_SERIALIZER, getString("reactions")),
    )

    internal fun <T> transaction(block: (Connection) -> T): T =
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

        /** 앱이 멘션을 감싸는 태그. 안쪽이 `@이름` 이다. */
        val MENTION_TAG = Regex("""<mention>([\s\S]*?)</mention>""", RegexOption.IGNORE_CASE)

        /** 대화를 읽지 않는 참여자(AI). 안읽음 수에 넣으면 AI 가 있는 방의 모든 대화가 영원히 "1" 로 남는다. */
        const val NON_READER_ID = "ai"

        /** 이 대화를 아직 읽지 않은 참여자 수. 나간 사람과 AI 는 세지 않는다. */
        const val UNREAD_COUNT =
            "(SELECT count(*) FROM chat_room_member mm " +
                "WHERE mm.room_id = g.room_id AND mm.left_at IS NULL AND mm.last_read_seq < g.seq " +
                "AND mm.user_id <> '$NON_READER_ID')"

        /** 이 대화에 눌린 공감들을 JSON 배열로. 대화마다 따로 조회하지 않게 한 쿼리에 싣는다. */
        const val REACTIONS =
            "(SELECT coalesce(json_agg(json_build_object('userId', r.user_id, 'userName', ru.name, 'kind', r.kind) " +
                "ORDER BY r.created_at), '[]') " +
                "FROM chat_reaction r JOIN app_user ru ON ru.id = r.user_id " +
                "WHERE r.room_id = g.room_id AND r.seq = g.seq)"

        val REACTIONS_SERIALIZER = ListSerializer(ReactionDto.serializer())

        /** 공감 종류는 앱의 이모지 여섯 개와 짝을 이룬다. */
        val REACTION_KINDS = setOf("0", "1", "2", "3", "4", "5")

        const val MESSAGE_SELECT =
            "SELECT g.room_id, g.seq, g.client_id, g.sender_id, u.name AS sender_name, g.kind, g.content, " +
                "g.payload, g.sent_at, g.recalled_at IS NOT NULL AS recalled, $UNREAD_COUNT AS unread, $REACTIONS AS reactions " +
                "FROM chat_message g JOIN app_user u ON u.id = g.sender_id"
    }
}

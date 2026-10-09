package com.eunilsung.talk.server.repository

import com.eunilsung.talk.shared.api.CreateVoteRequest
import com.eunilsung.talk.shared.api.MessageKind
import com.eunilsung.talk.shared.api.MessagePayloadDto
import com.eunilsung.talk.shared.api.VoteChangeResponse
import com.eunilsung.talk.shared.api.VoteDto
import com.eunilsung.talk.shared.api.VoteItemDto
import com.eunilsung.talk.shared.api.VoteVoterDto
import java.sql.Connection
import java.util.UUID

/**
 * 투표 저장소.
 *
 * 득표수는 저장하지 않고 표에서 센다. 만들 때와 끝낼 때는 방에 알림 대화를 남기므로, 대화의 순서를
 * 지키는 [ChatRepository] 의 잠금과 대화 넣기를 함께 쓴다.
 */
class VoteRepository(private val chats: ChatRepository) {

    /** 방의 투표 전부, 최근 것부터. 참여 중이 아니면 null. */
    fun votes(roomId: String, userId: String): List<VoteDto>? = chats.transaction { conn ->
        if (!chats.isActiveMember(conn, roomId, userId)) return@transaction null
        conn.prepareStatement("SELECT id FROM chat_vote WHERE room_id = ? ORDER BY created_at DESC").use { st ->
            st.setString(1, roomId)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }.mapNotNull { select(conn, roomId, it) }
    }

    /** 투표 한 건. 참여 중이 아니거나 이 방의 투표가 아니면 null. */
    fun vote(roomId: String, userId: String, voteId: String): VoteDto? = chats.transaction { conn ->
        if (!chats.isActiveMember(conn, roomId, userId)) return@transaction null
        select(conn, roomId, voteId)
    }

    /** 투표를 만들고 방에 알린다. 참여 중이 아니면 null. */
    fun create(roomId: String, userId: String, request: CreateVoteRequest): VoteChangeResponse? =
        chats.transaction { conn ->
            chats.lockRoom(conn, roomId) ?: return@transaction null
            if (!chats.isActiveMember(conn, roomId, userId)) return@transaction null

            val voteId = UUID.randomUUID().toString()
            conn.prepareStatement(
                """
                INSERT INTO chat_vote (id, room_id, title, writer_id, multi_select, allow_add_item, use_end_time, end_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { st ->
                st.setString(1, voteId)
                st.setString(2, roomId)
                st.setString(3, request.title)
                st.setString(4, userId)
                st.setBoolean(5, request.multiSelect)
                st.setBoolean(6, request.allowAddItem)
                st.setBoolean(7, request.useEndTime)
                st.setString(8, request.endTime)
                st.executeUpdate()
            }
            conn.prepareStatement(
                "INSERT INTO chat_vote_item (vote_id, idx, content, writer_id) VALUES (?, ?, ?, ?)"
            ).use { st ->
                request.items.forEachIndexed { index, content ->
                    st.setString(1, voteId)
                    st.setInt(2, index)
                    st.setString(3, content)
                    st.setString(4, userId)
                    st.addBatch()
                }
                st.executeBatch()
            }
            announce(conn, roomId, userId, voteId, MessageKind.VOTE)
        }

    /**
     * 내 표를 [selectedIdx] 로 바꾼다. 비우면 표를 거둔다. 바뀐 투표를 돌려준다.
     *
     * 참여 중이 아니거나, 끝난 투표이거나, 없는 항목을 골랐거나, 하나만 고르는 투표에 여럿을 골랐으면 null.
     */
    fun cast(roomId: String, userId: String, voteId: String, selectedIdx: List<Int>): VoteDto? =
        chats.transaction { conn ->
            if (!chats.isActiveMember(conn, roomId, userId)) return@transaction null
            val current = select(conn, roomId, voteId, forUpdate = true) ?: return@transaction null
            val picked = selectedIdx.distinct()
            val validIdx = current.items.map { it.idx }.toSet()
            if (current.isClosed || !validIdx.containsAll(picked)) return@transaction null
            if (!current.multiSelect && picked.size > 1) return@transaction null

            conn.prepareStatement("DELETE FROM chat_vote_ballot WHERE vote_id = ? AND user_id = ?").use { st ->
                st.setString(1, voteId)
                st.setString(2, userId)
                st.executeUpdate()
            }
            conn.prepareStatement("INSERT INTO chat_vote_ballot (vote_id, idx, user_id) VALUES (?, ?, ?)").use { st ->
                picked.forEach { idx ->
                    st.setString(1, voteId)
                    st.setInt(2, idx)
                    st.setString(3, userId)
                    st.addBatch()
                }
                st.executeBatch()
            }
            select(conn, roomId, voteId)
        }

    /** 투표를 끝내고 결과를 방에 알린다. 만든 사람만 할 수 있다. 이미 끝났으면 null. */
    fun close(roomId: String, userId: String, voteId: String): VoteChangeResponse? = chats.transaction { conn ->
        chats.lockRoom(conn, roomId) ?: return@transaction null
        if (!chats.isActiveMember(conn, roomId, userId)) return@transaction null
        val closed = conn.prepareStatement(
            """
            UPDATE chat_vote SET closed_at = now()
            WHERE id = ? AND room_id = ? AND writer_id = ? AND closed_at IS NULL
            """.trimIndent()
        ).use { st ->
            st.setString(1, voteId)
            st.setString(2, roomId)
            st.setString(3, userId)
            st.executeUpdate() > 0
        }
        if (!closed) return@transaction null
        announce(conn, roomId, userId, voteId, MessageKind.VOTE_CLOSED)
    }

    /**
     * 지금의 투표 모습을 알림 대화에 실어 방에 남긴다.
     *
     * 알림에는 누가 어디에 표를 줬는지를 싣지 않는다. 대화는 오래 남고, 그 정보는 결과 화면에서만 필요하다.
     */
    private fun announce(conn: Connection, roomId: String, actorId: String, voteId: String, kind: String): VoteChangeResponse {
        val vote = checkNotNull(select(conn, roomId, voteId))
        val message = chats.insertMessage(
            conn = conn,
            roomId = roomId,
            senderId = actorId,
            clientId = UUID.randomUUID().toString(),
            kind = kind,
            content = vote.title,
            payload = MessagePayloadDto(vote = vote.copy(voters = emptyList())),
        )
        return VoteChangeResponse(vote, message)
    }

    private fun select(conn: Connection, roomId: String, voteId: String, forUpdate: Boolean = false): VoteDto? {
        val lockClause = if (forUpdate) "FOR UPDATE" else ""
        val base = conn.prepareStatement(
            """
            SELECT id, title, writer_id, closed_at IS NOT NULL, multi_select, allow_add_item, use_end_time, end_time
            FROM chat_vote WHERE id = ? AND room_id = ? $lockClause
            """.trimIndent()
        ).use { st ->
            st.setString(1, voteId)
            st.setString(2, roomId)
            st.executeQuery().use { rs ->
                if (!rs.next()) return null
                VoteDto(
                    id = rs.getString(1),
                    title = rs.getString(2),
                    writerId = rs.getString(3),
                    isClosed = rs.getBoolean(4),
                    multiSelect = rs.getBoolean(5),
                    allowAddItem = rs.getBoolean(6),
                    useEndTime = rs.getBoolean(7),
                    endTime = rs.getString(8),
                )
            }
        }
        val voters = conn.prepareStatement(
            """
            SELECT b.idx, b.user_id, u.name
            FROM chat_vote_ballot b JOIN app_user u ON u.id = b.user_id
            WHERE b.vote_id = ? ORDER BY b.voted_at, b.idx
            """.trimIndent()
        ).use { st ->
            st.setString(1, voteId)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(VoteVoterDto(rs.getInt(1), rs.getString(2), rs.getString(3))) }
            }
        }
        val items = conn.prepareStatement(
            "SELECT idx, content, writer_id FROM chat_vote_item WHERE vote_id = ? ORDER BY idx"
        ).use { st ->
            st.setString(1, voteId)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val idx = rs.getInt(1)
                        add(VoteItemDto(idx, rs.getString(2), voters.count { it.itemIdx == idx }, rs.getString(3)))
                    }
                }
            }
        }
        return base.copy(items = items, voters = voters)
    }
}

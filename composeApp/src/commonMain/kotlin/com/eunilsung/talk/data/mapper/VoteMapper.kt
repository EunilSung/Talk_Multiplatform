package com.eunilsung.talk.data.mapper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.eunilsung.talk.db.VoteEntity
import com.eunilsung.talk.domain.model.VoteData
import com.eunilsung.talk.domain.model.VoteDataItem
import com.eunilsung.talk.domain.model.VoteSummary
import com.eunilsung.talk.domain.model.VoteVoter
import com.eunilsung.talk.util.Log

/** 투표 도메인 <-> DB 매퍼. 항목·투표자 목록은 [Json] 으로 인코딩해 TEXT 컬럼에 저장한다. */
class VoteMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun toEntity(myId: String, chatRoomId: String, data: VoteData): VoteEntity = VoteEntity(
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

    fun toData(entity: VoteEntity): VoteData {
        val items = decode<ItemRecord>(entity.itemsJson).map { it.toModel() }
        val voters = decode<VoterRecord>(entity.votersJson).map { it.toModel() }
        return VoteData(
            id = entity.voteId,
            title = entity.title,
            isClosed = entity.isClosed.toBool(),
            useEndTime = entity.useEndTime.toBool(),
            endTime = entity.endTime.orEmpty(),
            participantCount = voters.map { it.userID }.distinct().size,
            writeUserId = entity.writeUserId,
            multiSelect = entity.multiSelect.toBool(),
            allowAddItem = entity.allowAddItem.toBool(),
            items = items,
            voters = voters,
        )
    }

    /** 투표 목록 한 줄. 내가 만들었는지·투표했는지는 [myId] 로 가린다. */
    fun toSummary(data: VoteData, myId: String): VoteSummary = VoteSummary(
        id = data.id,
        title = data.title,
        itemCount = data.items.size,
        participantCount = data.participantCount,
        isClosed = data.isClosed,
        endTime = data.endTime,
        isMine = data.writeUserId.equals(myId, ignoreCase = true),
        hasVoted = data.hasVoted(myId),
    )

    private inline fun <reified T> decode(raw: String): List<T> =
        runCatching { json.decodeFromString<List<T>>(raw) }.getOrElse {
            Log.message("[Vote/Local] decode failed: ${it.message}")
            emptyList()
        }

    /** 저장 전용 항목 형태. 도메인 모델에는 직렬화 애너테이션이 없다. */
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

    /** 저장 전용 투표자 형태. */
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

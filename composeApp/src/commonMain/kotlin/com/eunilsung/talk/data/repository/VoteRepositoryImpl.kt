package com.eunilsung.talk.data.repository

import com.eunilsung.talk.Config
import com.eunilsung.talk.data.mapper.ServerChatMapper
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.data.remote.server.valueOrNull
import com.eunilsung.talk.data.sample.LocalChatRoomRepositoryImpl
import com.eunilsung.talk.domain.model.VoteData
import com.eunilsung.talk.domain.model.VoteForm
import com.eunilsung.talk.domain.model.VoteSummary
import com.eunilsung.talk.domain.repository.VoteRepository
import com.eunilsung.talk.shared.api.CreateVoteRequest
import com.eunilsung.talk.shared.api.VoteChangeResponse

/**
 * 서버의 투표를 다루는 저장소.
 *
 * 투표는 기기에 따로 저장하지 않고 볼 때마다 서버에서 받는다. 다른 사람의 표가 계속 들어오는
 * 것이라, 저장해 둔 값은 금세 낡는다.
 */
class VoteRepositoryImpl(
    private val server: TalkServer,
    private val localChats: LocalChatRoomRepositoryImpl,
    private val mapper: ServerChatMapper,
) : VoteRepository {

    override suspend fun fetchVotes(chatRoomId: String): List<VoteSummary> {
        val myId = Config.MyInfo.userId
        return server.votes(chatRoomId).valueOrNull().orEmpty().map { mapper.toVoteData(it) }.map { data ->
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
        server.vote(chatRoomId, voteId).valueOrNull()?.let(mapper::toVoteData)

    override suspend fun createVote(chatRoomId: String, form: VoteForm): Result<String> {
        if (!form.canSubmit) return Result.failure(IllegalArgumentException("제목과 항목 2개 이상이 필요하다"))
        val request = CreateVoteRequest(
            title = form.title,
            items = form.items.map { it.content }.filter { it.isNotBlank() },
            multiSelect = form.multiSelect,
            allowAddItem = form.allowAddItem,
            useEndTime = form.useEndTime,
            endTime = form.endTime,
        )
        val change = server.createVote(chatRoomId, request).valueOrNull()
            ?: return Result.failure(IllegalStateException("투표를 만들지 못했다"))
        showAnnouncement(chatRoomId, change)
        return Result.success(change.vote.id)
    }

    override suspend fun submitVote(chatRoomId: String, voteId: String, selectedIdx: Set<Int>): VoteData? =
        server.castVote(chatRoomId, voteId, selectedIdx.toList()).valueOrNull()?.let(mapper::toVoteData)

    /** 표를 비워 보내는 것이 "다시 투표하기"다. */
    override suspend fun reVote(chatRoomId: String, voteId: String): VoteData? =
        server.castVote(chatRoomId, voteId, emptyList()).valueOrNull()?.let(mapper::toVoteData)

    override suspend fun closeVote(chatRoomId: String, voteId: String, title: String) {
        val change = server.closeVote(chatRoomId, voteId).valueOrNull() ?: return
        showAnnouncement(chatRoomId, change)
    }

    /**
     * 투표를 만들거나 끝냈다는 알림 대화를 바로 대화창에 넣는다.
     *
     * 알림으로도 오지만 그것을 기다리지 않는다. 연결이 끊겨 있어도 방금 한 일이 화면에 보여야 한다.
     * 같은 id 라 나중에 알림이 와도 한 줄로 남는다.
     */
    private suspend fun showAnnouncement(chatRoomId: String, change: VoteChangeResponse) {
        localChats.storeChats(chatRoomId, listOf(mapper.toChat(change.message)))
    }
}

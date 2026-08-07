package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.VoteData
import com.eunilsung.talk.domain.model.VoteForm
import com.eunilsung.talk.domain.model.VoteSummary
import com.eunilsung.talk.domain.repository.VoteRepository

data class VoteUseCases(
    val getVotes: GetVotesUseCase,
    val getVote: GetVoteUseCase,
    val createVote: CreateVoteUseCase,
    val submitVote: SubmitVoteUseCase,
    val reVote: ReVoteUseCase,
    val closeVote: CloseVoteUseCase,
)

class GetVotesUseCase(private val repository: VoteRepository) {
    suspend operator fun invoke(chatRoomId: String): List<VoteSummary> =
        repository.fetchVotes(chatRoomId)
}

class GetVoteUseCase(private val repository: VoteRepository) {
    suspend operator fun invoke(chatRoomId: String, voteId: String): VoteData? =
        repository.fetchVote(chatRoomId, voteId)
}

class CreateVoteUseCase(private val repository: VoteRepository) {
    suspend operator fun invoke(chatRoomId: String, form: VoteForm): Result<String> =
        repository.createVote(chatRoomId, form)
}

class SubmitVoteUseCase(private val repository: VoteRepository) {
    suspend operator fun invoke(chatRoomId: String, voteId: String, selectedIdx: Set<Int>): VoteData? =
        repository.submitVote(chatRoomId, voteId, selectedIdx)
}

class ReVoteUseCase(private val repository: VoteRepository) {
    suspend operator fun invoke(chatRoomId: String, voteId: String): VoteData? =
        repository.reVote(chatRoomId, voteId)
}

class CloseVoteUseCase(private val repository: VoteRepository) {
    suspend operator fun invoke(chatRoomId: String, voteId: String, title: String) =
        repository.closeVote(chatRoomId, voteId, title)
}

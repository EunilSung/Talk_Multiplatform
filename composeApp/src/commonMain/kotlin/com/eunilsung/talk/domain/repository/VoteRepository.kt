package com.eunilsung.talk.domain.repository

import com.eunilsung.talk.domain.model.VoteData
import com.eunilsung.talk.domain.model.VoteForm
import com.eunilsung.talk.domain.model.VoteSummary

/** 투표 데이터 소스 추상화. */
interface VoteRepository {
    /** 대화방의 투표 리스트. */
    suspend fun fetchVotes(chatRoomId: String): List<VoteSummary>

    /** 단일 투표 상세 — 없으면 null. */
    suspend fun fetchVote(chatRoomId: String, voteId: String): VoteData?

    /** 투표 생성 — 성공 시 새 투표 id. */
    suspend fun createVote(chatRoomId: String, form: VoteForm): Result<String>

    /** 투표 참여 — 갱신된 [VoteData], 실패 시 null. */
    suspend fun submitVote(chatRoomId: String, voteId: String, selectedIdx: Set<Int>): VoteData?

    /** 투표 다시하기 — 갱신된 [VoteData], 실패 시 null. */
    suspend fun reVote(chatRoomId: String, voteId: String): VoteData?

    /** 투표 종료(생성자만 가능). */
    suspend fun closeVote(chatRoomId: String, voteId: String, title: String)
}

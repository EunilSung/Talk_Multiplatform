package com.eunilsung.talk.ui.chatroom.vote

import com.eunilsung.talk.domain.model.VoteData
import com.eunilsung.talk.domain.model.VoteForm
import com.eunilsung.talk.domain.model.VoteResultItem
import com.eunilsung.talk.domain.model.VoteSummary

sealed interface VoteUiState {
    val mode: VoteMode

    data object Loading : VoteUiState {
        override val mode: VoteMode get() = VoteMode.LIST
    }

    data class List(
        val votes: kotlin.collections.List<VoteSummary> = emptyList(),
        val isRefreshing: Boolean = false,
    ) : VoteUiState {
        override val mode: VoteMode get() = VoteMode.LIST
    }

    data class Create(
        val form: VoteForm = VoteForm(),
        val isSubmitting: Boolean = false,
    ) : VoteUiState {
        override val mode: VoteMode get() = VoteMode.CREATE
    }

    data class Participate(
        val detail: VoteDetail,
        val selected: Set<Int> = emptySet(),
        val isSubmitting: Boolean = false,
    ) : VoteUiState {
        override val mode: VoteMode get() = VoteMode.PARTICIPATE
    }

    data class Result(
        val result: VoteResult,
    ) : VoteUiState {
        override val mode: VoteMode get() = VoteMode.RESULT
    }
}

/** 투표 참여 화면용 상세. */
data class VoteDetail(
    val id: String = "",
    val title: String = "",
    val options: kotlin.collections.List<VoteOption> = emptyList(),
    val multiSelect: Boolean = false,
    val anonymous: Boolean = false,
    val allowAddItem: Boolean = false,
    val useEndTime: Boolean = false,
    val endTime: String = "",
    val isClosed: Boolean = false,
    val hasVoted: Boolean = false,
)

/** 참여 화면의 선택지 한 개. */
data class VoteOption(
    val idx: Int = 0,
    val content: String = "",
)

/** 투표 결과 화면용 — 항목별 득표 포함. [items] 의 content/nVote 는 [VoteResultItem] 재사용. */
data class VoteResult(
    val id: String = "",
    val title: String = "",
    val totalVotes: Int = 0,
    val items: kotlin.collections.List<VoteResultItem> = emptyList(),
    val multiSelect: Boolean = false,
    val anonymous: Boolean = false,
    val isClosed: Boolean = false,
    val hasVoted: Boolean = false,
    val isMine: Boolean = false,
) {
    /** 진행중인데 내가 이미 투표 → "투표 다시하기" 가능. */
    val canReVote: Boolean get() = !isClosed && hasVoted

    /** 진행중인데 내가 만든 투표 → "투표종료" 가능. */
    val canClose: Boolean get() = !isClosed && isMine
}

/** 참여(투표하기) 화면용 모델로 변환. */
internal fun VoteData.toDetail(myId: String): VoteDetail = VoteDetail(
    id = id,
    title = title,
    options = items.sortedBy { it.seq }.map { VoteOption(idx = it.idx, content = it.content) },
    multiSelect = multiSelect,
    allowAddItem = allowAddItem,
    useEndTime = useEndTime,
    endTime = endTime,
    isClosed = isClosed,
    hasVoted = hasVoted(myId),
)

/** 결과 화면용 모델로 변환. [VoteResult.hasVoted] 로 "투표 다시하기" 노출 판정. */
internal fun VoteData.toResult(myId: String): VoteResult = VoteResult(
    id = id,
    title = title,
    totalVotes = totalVotes,
    items = items.sortedBy { it.seq }.map {
        VoteResultItem(
            idx = it.idx,
            seq = it.seq,
            content = it.content,
            nVote = it.nVote,
            writeUserId = it.writeUserId,
        )
    },
    multiSelect = multiSelect,
    isClosed = isClosed,
    hasVoted = hasVoted(myId),
    isMine = writeUserId.equals(myId, ignoreCase = true),
)

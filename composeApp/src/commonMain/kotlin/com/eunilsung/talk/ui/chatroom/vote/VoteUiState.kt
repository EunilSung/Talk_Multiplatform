package com.eunilsung.talk.ui.chatroom.vote

import com.eunilsung.talk.domain.model.VoteDetail
import com.eunilsung.talk.domain.model.VoteForm
import com.eunilsung.talk.domain.model.VoteResult
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

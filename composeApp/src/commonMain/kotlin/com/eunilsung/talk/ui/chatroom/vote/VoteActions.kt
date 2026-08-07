package com.eunilsung.talk.ui.chatroom.vote

import com.eunilsung.talk.domain.model.VoteSummary

sealed interface VoteActions {
    data class Load(
        val mode: VoteMode,
        val voteId: String? = null,
        val chatRoomId: String = "",
    ) : VoteActions

    data object OnRefresh : VoteActions

    data object OnBack : VoteActions

    data object OnCreateClick : VoteActions

    data class OnVoteClick(val summary: VoteSummary) : VoteActions

    data class OnTitleChange(val title: String) : VoteActions
    data class OnItemContentChange(val id: Int, val content: String) : VoteActions
    data object OnAddItem : VoteActions
    data class OnRemoveItem(val id: Int) : VoteActions
    data class OnToggleMultiSelect(val enabled: Boolean) : VoteActions
    data class OnToggleAnonymous(val enabled: Boolean) : VoteActions
    data class OnToggleAllowAddItem(val enabled: Boolean) : VoteActions
    data class OnToggleUseEndTime(val enabled: Boolean) : VoteActions
    data class OnEndTimeChange(val endTime: String) : VoteActions
    data object OnSubmitCreate : VoteActions

    data class OnToggleOption(val idx: Int) : VoteActions
    data object OnSubmitVote : VoteActions

    data object OnViewResult : VoteActions

    data object OnReVote : VoteActions

    data object OnCloseVote : VoteActions
}

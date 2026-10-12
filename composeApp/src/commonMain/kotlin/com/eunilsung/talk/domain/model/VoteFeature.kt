package com.eunilsung.talk.domain.model

/** 투표 기능(리스트/생성/참여/결과)의 도메인 모델 모음. */

/** 투표 리스트의 한 항목 요약. */
data class VoteSummary(
    val id: String = "",
    val title: String = "",
    val itemCount: Int = 0,
    val participantCount: Int = 0,
    val isClosed: Boolean = false,
    val endTime: String = "",
    val isMine: Boolean = false,
    val hasVoted: Boolean = false,
)

/** 투표 생성 폼 상태 (기본 항목 2개로 시작). */
data class VoteForm(
    val title: String = "",
    val items: List<VoteFormItem> = listOf(VoteFormItem(id = 0), VoteFormItem(id = 1)),
    val multiSelect: Boolean = false,
    val anonymous: Boolean = false,
    val allowAddItem: Boolean = false,
    val useEndTime: Boolean = false,
    val endTime: String = "",
) {
    /** 제목 + 내용 있는 항목 2개 이상이어야 제출 가능. */
    val canSubmit: Boolean
        get() = title.isNotBlank() && items.count { it.content.isNotBlank() } >= 2
}

/** 생성 폼의 항목 한 개. [id] 는 리스트 편집용 안정 키. */
data class VoteFormItem(
    val id: Int = 0,
    val content: String = "",
)

/** 투표 상세 — 참여 화면과 결과 화면이 공유하는 원본. */
data class VoteData(
    val id: String = "",
    val title: String = "",
    val isClosed: Boolean = false,
    val useEndTime: Boolean = false,
    val endTime: String = "",
    val participantCount: Int = 0,
    val writeUserId: String = "",
    val multiSelect: Boolean = false,
    val allowAddItem: Boolean = false,
    val items: List<VoteDataItem> = emptyList(),
    val voters: List<VoteVoter> = emptyList(),
) {
    /** 내가 선택한 항목 idx (미투표 -1). */
    fun myItemIdx(myId: String): Int =
        voters.firstOrNull { it.userID.equals(myId, ignoreCase = true) }?.itemIdx ?: -1

    /** 내가 이미 투표했는지. */
    fun hasVoted(myId: String): Boolean = myItemIdx(myId) != -1

    val totalVotes: Int get() = items.sumOf { it.nVote }
}

/** 투표 항목(slot[5]) 한 개. content 는 Base64 디코드 후. */
data class VoteDataItem(
    val idx: Int = 0,
    val seq: Int = 0,
    val content: String = "",
    val nVote: Int = 0,
    val writeUserId: String = "",
)

/** 사용자 투표 현황(slot[6]) 한 명. [itemIdx] 가 -1 이면 미투표. */
data class VoteVoter(
    val itemIdx: Int = -1,
    val userID: String = "",
    val userName: String = "",
    val voteDate: String = "",
)

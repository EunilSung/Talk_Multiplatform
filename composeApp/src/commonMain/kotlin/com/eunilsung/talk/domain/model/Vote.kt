package com.eunilsung.talk.domain.model

/** 투표 대화([Chat.Type.VOTE]) 의 디코드된 표현. */
data class Vote(
    val id: String = "",
    val items: List<String> = emptyList(),
    val itemType: String = "TEXT",
    val setting: VoteSetting = VoteSetting(),
)

/** 투표 설정. */
data class VoteSetting(
    val settingEndTime: Boolean = false,
    val endTime: String = "",
    val multiSelect: Boolean = false,
    val anonymous: Boolean = false,
    val allowAddItem: Boolean = false,
)

/** 완료된 투표([Chat.Type.VOTE_COMPLETE]) 의 디코드된 표현 — 항목별 득표 결과 포함. */
data class VoteComplete(
    val id: String = "",
    val items: List<VoteResultItem> = emptyList(),
)

/** 완료 투표의 항목별 결과. */
data class VoteResultItem(
    val idx: Int = 0,
    val seq: Int = 0,
    val content: String = "",
    val nVote: Int = 0,
    val writeUserId: String = "",
)

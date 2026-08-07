package com.eunilsung.talk.domain.model

/** 이모티콘 첨부 정보 (식별자 + 타입). */
data class Emoticon(
    var id: String = "",
    var type: Int = 0,
)

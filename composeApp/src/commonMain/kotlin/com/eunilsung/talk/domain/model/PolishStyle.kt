package com.eunilsung.talk.domain.model

/** 보내기 전의 글을 어떻게 다듬을지. */
enum class PolishStyle {
    /** 맞춤법·띄어쓰기·오타만 고친다. */
    CORRECT,

    /** 같은 내용을 공손한 말투로 바꾼다. */
    POLITE,

    /** 같은 내용을 짧게 줄인다. */
    CONCISE,
}

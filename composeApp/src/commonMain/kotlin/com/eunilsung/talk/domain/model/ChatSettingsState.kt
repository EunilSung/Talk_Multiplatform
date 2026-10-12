package com.eunilsung.talk.domain.model

/** 대화 설정 — Enter 로 보내기, 말풍선 글자 크기. */
data class ChatSettingsState(
    val enterToSend: Boolean = false,
    val fontSize: Int = DEFAULT_FONT_SIZE,
) {
    companion object {
        /** 사용자가 선택 가능한 글자 크기 (sp). */
        val FONT_SIZE_OPTIONS: List<Int> = listOf(13, 14, 15, 17, 19, 22, 25)
        /** 기본 글자 크기. */
        const val DEFAULT_FONT_SIZE: Int = 15
    }
}

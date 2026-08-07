package com.eunilsung.talk.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** 대화(채팅) 관련 사용자 설정. */
interface ChatSettingsRepository {
    val state: StateFlow<ChatSettingsState>

    fun setEnterToSend(enabled: Boolean)

    /** [FONT_SIZE_OPTIONS] 중 하나여야 함. 다른 값이면 가장 가까운 값으로 자동 보정. */
    fun setFontSize(size: Int)

    /** 마지막 선택한 이모티콘 탭 인덱스 — 대화방(전체 탭). 영속. 저장값 없으면 0. */
    fun getLastEmoticonTab(): Int

    /** 마지막 선택한 이모티콘 탭 인덱스 저장 — 대화방. */
    fun setLastEmoticonTab(index: Int)

    companion object {
        /** 사용자가 선택 가능한 글자 크기 (sp). */
        val FONT_SIZE_OPTIONS: List<Int> = listOf(13, 14, 15, 17, 19, 22, 25)
        /** 기본 글자 크기. */
        const val DEFAULT_FONT_SIZE: Int = 15
    }
}

data class ChatSettingsState(
    val enterToSend: Boolean = false,
    val fontSize: Int = ChatSettingsRepository.DEFAULT_FONT_SIZE,
)

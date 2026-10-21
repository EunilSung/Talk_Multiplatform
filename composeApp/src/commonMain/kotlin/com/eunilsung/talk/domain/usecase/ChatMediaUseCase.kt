package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.repository.ChatRoomRepository

/** 대화방 모아보기 화면이 쓰는 일 — 방의 첨부 읽기와 파일 받기·열기. */
data class ChatMediaUseCases(
    val getMediaChats: GetMediaChatsUseCase,
    val downloadFile: DownloadFileUseCase,
    val openFile: OpenFileUseCase,
)

/** 모아보기 — 이 기기에 받아 둔 방의 사진·동영상·파일, 최근 것이 먼저. */
class GetMediaChatsUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String): List<Chat.Item> = repository.getMediaChats(chatRoomId)
}

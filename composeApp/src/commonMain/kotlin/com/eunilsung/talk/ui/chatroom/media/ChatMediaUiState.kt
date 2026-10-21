package com.eunilsung.talk.ui.chatroom.media

import com.eunilsung.talk.domain.model.Chat

enum class ChatMediaTab { PHOTO_VIDEO, FILE }

/**
 * 모아보기 상태.
 *
 * @param photosAndVideos 사진·동영상 탭 — 최근 것이 먼저.
 * @param files 파일 탭 — 최근 것이 먼저.
 * @param galleryMedia 사진·동영상을 눌렀을 때 좌우로 넘길 목록 — 대화방 사진 상세보기와 같은 **오래된 순**.
 */
data class ChatMediaUiState(
    val chatRoomId: String = "",
    val tab: ChatMediaTab = ChatMediaTab.PHOTO_VIDEO,
    val isLoading: Boolean = true,
    val photosAndVideos: List<Chat.Item> = emptyList(),
    val files: List<Chat.Item> = emptyList(),
    val galleryMedia: List<Chat.Item> = emptyList(),
)

/** 방의 첨부 대화([mediaChats], 최근 것이 먼저)를 탭별로 나눈다. 사진·동영상·파일 말고는 버린다. */
fun ChatMediaUiState.withMedia(mediaChats: List<Chat.Item>): ChatMediaUiState {
    val photosAndVideos = mediaChats.filter { it.chatType == Chat.Type.IMAGE || it.chatType == Chat.Type.VIDEO }
    return copy(
        isLoading = false,
        photosAndVideos = photosAndVideos,
        files = mediaChats.filter { it.chatType == Chat.Type.FILE },
        galleryMedia = photosAndVideos.reversed(),
    )
}

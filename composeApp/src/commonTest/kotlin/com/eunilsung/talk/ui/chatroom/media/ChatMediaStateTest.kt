package com.eunilsung.talk.ui.chatroom.media

import com.eunilsung.talk.domain.model.Chat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 모아보기 상태 — 첨부를 탭별로 나누고, 넘겨보기 목록은 대화방 사진 상세보기처럼 오래된 순인지. */
class ChatMediaStateTest {

    private fun chat(id: String, type: String) = Chat.Item(chatID = id, chatType = type, imagePath = "/cache/$id")

    @Test
    fun 사진_동영상과_파일을_탭별로_나누고_최근_순서를_지킨다() {
        val recentFirst = listOf(
            chat("v3", Chat.Type.VIDEO),
            chat("f2", Chat.Type.FILE),
            chat("p1", Chat.Type.IMAGE),
            chat("t0", Chat.Type.TEXT),
        )

        val state = ChatMediaUiState(chatRoomId = "room").withMedia(recentFirst)

        assertFalse(state.isLoading)
        assertEquals(listOf("v3", "p1"), state.photosAndVideos.map { it.chatID })
        assertEquals(listOf("f2"), state.files.map { it.chatID })
        assertEquals(listOf("p1", "v3"), state.galleryMedia.map { it.chatID })
    }
}

package com.eunilsung.talk.ui.chatroom

import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.GroupedChat
import com.eunilsung.talk.ui.uikit.mediapicker.MultimediaRecentPhoto

/** 사진 상세보기에서 좌우로 넘길 사진 목록과 처음 보여 줄 위치. */
data class ChatImageGallery(
    val photos: List<MultimediaRecentPhoto>,
    val startIndex: Int,
)

/**
 * 대화 목록([groupedChats], 최신순)에서 사진·동영상 대화를 오래된 순으로 뽑아 [clicked] 위치부터 연다.
 *
 * 회수된 대화와 파일 경로가 없는 대화는 뺀다. 누른 사진이 목록에 없으면 그 한 장만 연다 — 넘기기가 안 될 뿐
 * 누른 사진은 반드시 열린다.
 */
fun chatImageGallery(groupedChats: List<GroupedChat>, clicked: Chat.Item): ChatImageGallery {
    val photos = groupedChats.asReversed()
        .map { it.chat }
        .filter { it.isGalleryMedia() }
        .map { it.toDetailPhoto() }
    val index = photos.indexOfFirst { it.id == clicked.chatID }
    return if (index >= 0) {
        ChatImageGallery(photos, index)
    } else {
        ChatImageGallery(listOf(clicked.toDetailPhoto()), 0)
    }
}

private fun Chat.Item.isGalleryMedia(): Boolean =
    (chatType == Chat.Type.IMAGE || chatType == Chat.Type.VIDEO) && !isRecalled && imagePath.isNotBlank()

/** 사진·동영상 대화를 상세보기 한 장으로. 제목에는 보낸 사람과 보낸 시간을 보인다. */
fun Chat.Item.toDetailPhoto(): MultimediaRecentPhoto = MultimediaRecentPhoto(
    id = chatID,
    uri = imagePath,
    serverFileName = imagePath,
    originalFileName = originalFileName,
    isVideo = chatType == Chat.Type.VIDEO,
    senderName = user.name,
    sentDate = date,
)

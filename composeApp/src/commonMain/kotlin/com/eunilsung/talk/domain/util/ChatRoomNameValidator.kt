package com.eunilsung.talk.domain.util

/** 대화방 이름 검증 결과. */
enum class ChatRoomNameValidation {
    Valid,
    Empty,
    TooLong
}

/** 대화방 이름의 최대 글자 수. */
const val CHAT_ROOM_NAME_MAX_LENGTH = 20

/** 앞뒤 공백을 뺀 [newName] 이 대화방 이름으로 쓸 수 있는지. */
fun validateChatRoomName(newName: String): ChatRoomNameValidation {
    val trimmed = newName.trim()
    return when {
        trimmed.isEmpty() -> ChatRoomNameValidation.Empty
        trimmed.length > CHAT_ROOM_NAME_MAX_LENGTH -> ChatRoomNameValidation.TooLong
        else -> ChatRoomNameValidation.Valid
    }
}

package com.eunilsung.talk.shared.api

import kotlinx.serialization.Serializable

/** 대화방 참여자. 나간 사람도 [hasLeft] 를 세운 채 남는다 — 1:1 방에서 떠난 상대 이름을 보여 줘야 한다. */
@Serializable
data class RoomMemberDto(
    val id: String,
    val name: String,
    val hasLeft: Boolean = false,
)

/** 대화방 하나 — 요청한 사람의 눈으로 본 모습이다. 안읽음 수와 알림 설정은 사람마다 다르다. */
@Serializable
data class RoomDto(
    val id: String,
    /** 누군가 붙인 방 이름. 비어 있으면 앱이 참여자 이름으로 만든다. */
    val title: String = "",
    val members: List<RoomMemberDto> = emptyList(),
    val lastMessage: MessageDto? = null,
    /** 내가 아직 읽지 않은 대화 수. */
    val unreadCount: Int = 0,
    val isMuted: Boolean = false,
    /** 방이 만들어진 시각. 아직 대화가 없는 방을 목록에 세울 때 쓴다. */
    val createdAtEpochMillis: Long = 0,
)

@Serializable
data class RoomsResponse(val rooms: List<RoomDto> = emptyList())

/**
 * 대화방 만들기. [memberIds] 에 나를 넣지 않아도 서버가 넣는다.
 *
 * 상대가 한 명이면 1:1 방이다. 그 사람과의 방이 이미 있으면 새로 만들지 않고 그 방을 돌려준다.
 * 나만 넣으면 나와의 대화방이 된다.
 */
@Serializable
data class CreateRoomRequest(val memberIds: List<String>)

@Serializable
data class InviteRequest(val userIds: List<String>)

@Serializable
data class RenameRoomRequest(val title: String)

@Serializable
data class MuteRoomRequest(val isMuted: Boolean)

/**
 * 대화 한 건.
 *
 * [id] 는 보낸 앱이 만든 값이다. 응답을 못 받아 다시 보내도 서버가 이 값으로 같은 대화임을 알아본다.
 * [seq] 는 서버가 방 안에서 매긴 순서다. 시각은 화면 표시용이고 순서는 [seq] 가 정한다 —
 * 단말 시계가 어긋나도 순서가 뒤집히지 않는다.
 */
@Serializable
data class MessageDto(
    val roomId: String,
    val seq: Long,
    val id: String,
    val senderId: String,
    val senderName: String = "",
    val kind: String = MessageKind.TEXT,
    val content: String = "",
    val payload: MessagePayloadDto? = null,
    val sentAtEpochMillis: Long = 0,
    /** 이 대화를 아직 읽지 않은 참여자 수. */
    val unreadCount: Int = 0,
)

/** 대화 종류. 본문만으로는 답장인지 이모티콘인지 알 수 없어, 받는 쪽이 같은 화면을 그리려면 함께 와야 한다. */
object MessageKind {
    const val TEXT = "text"
    const val EMOTICON = "emoticon"
    const val REPLY = "reply"
    /** 초대 알림. 보낸 사람이 초대한 사람이고, 초대받은 사람은 [MessagePayloadDto.targetNames] 에 있다. */
    const val INVITE = "invite"
    /** 퇴장 알림. 보낸 사람이 나간 사람이다. */
    const val EXIT = "exit"

    /** 앱이 직접 보낼 수 있는 종류. 초대·퇴장은 서버만 만든다. */
    val SENDABLE = setOf(TEXT, EMOTICON, REPLY)
}

/**
 * 종류별 부가 정보.
 *
 * 답장의 인용은 원본을 가리키기만 하지 않고 그 시점 내용을 함께 담는다. 원본이 회수되거나 아직
 * 받지 않은 구간에 있어도 인용은 보여야 한다. [replyToId] 는 "원본으로 이동"에 쓴다.
 */
@Serializable
data class MessagePayloadDto(
    val emoticonId: String? = null,
    val replyToId: String? = null,
    val replyKind: String? = null,
    val replyAuthorId: String? = null,
    val replyAuthorName: String? = null,
    val replyText: String? = null,
    val replyEmoticonId: String? = null,
    val targetNames: List<String> = emptyList(),
)

@Serializable
data class SendMessageRequest(
    val id: String,
    val content: String = "",
    val kind: String = MessageKind.TEXT,
    val payload: MessagePayloadDto? = null,
)

@Serializable
data class MessagesResponse(val messages: List<MessageDto> = emptyList())

/** [messageId] 까지 읽었다. 더 앞의 대화를 가리키면 서버가 무시한다 — 읽은 자리는 뒤로 가지 않는다. */
@Serializable
data class MarkReadRequest(val messageId: String)

@Serializable
data class UnreadCountDto(val messageId: String, val unreadCount: Int)

@Serializable
data class UnreadCountsResponse(val counts: List<UnreadCountDto> = emptyList())

/**
 * WebSocket 으로 오는 알림.
 *
 * 알림은 "무엇이 바뀌었다"만 전한다. 놓쳐도 다음 조회에서 같은 상태에 도달하도록, 알림에만 실려
 * 오는 정보는 두지 않는다.
 */
@Serializable
data class ServerEvent(
    val type: String,
    val roomId: String,
    val message: MessageDto? = null,
) {
    companion object {
        /** 새 대화. [message] 가 함께 온다. */
        const val TYPE_MESSAGE = "message"
        /** 누군가 읽어서 안읽음 수가 바뀌었다. */
        const val TYPE_READ = "read"
        /** 방 정보(이름·참여자)가 바뀌었다. */
        const val TYPE_ROOM = "room"
    }
}

/** 방과 대화 요청에서 추가로 오는 [ApiError.code] 값. */
object ChatErrorCode {
    /** 그런 방이 없거나 내가 참여자가 아니다. 둘을 가르지 않는다 — 가르면 방이 있는지 알아낼 수 있다. */
    const val ROOM_NOT_FOUND = "room_not_found"
    const val USER_NOT_FOUND = "user_not_found"
}

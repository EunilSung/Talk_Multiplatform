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
    /** 보낸 사람이 회수했다. 회수된 대화는 본문과 부가 정보가 비어서 온다. */
    val isRecalled: Boolean = false,
    /** 이 대화에 눌린 공감들. */
    val reactions: List<ReactionDto> = emptyList(),
)

/** 대화 종류. 본문만으로는 답장인지 이모티콘인지 알 수 없어, 받는 쪽이 같은 화면을 그리려면 함께 와야 한다. */
object MessageKind {
    const val TEXT = "text"
    const val EMOTICON = "emoticon"
    const val REPLY = "reply"
    /** 사진·동영상·파일. 실제 파일은 먼저 올리고, 받은 id 를 [MessagePayloadDto.fileId] 에 실어 보낸다. */
    const val IMAGE = "image"
    const val VIDEO = "video"
    const val FILE = "file"
    /** 초대 알림. 보낸 사람이 초대한 사람이고, 초대받은 사람은 [MessagePayloadDto.targetNames] 에 있다. */
    const val INVITE = "invite"
    /** 퇴장 알림. 보낸 사람이 나간 사람이다. */
    const val EXIT = "exit"
    /** 공지 등록·삭제 알림. 본문이 공지 내용이고, 어느 쪽인지는 [MessagePayloadDto.noticeAction] 에 있다. */
    const val NOTICE = "notice"

    /** 앱이 직접 보낼 수 있는 종류. 초대·퇴장은 서버만 만든다. */
    val SENDABLE = setOf(TEXT, EMOTICON, REPLY, IMAGE, VIDEO, FILE)

    /** 파일이 딸린 종류. */
    val WITH_FILE = setOf(IMAGE, VIDEO, FILE)
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
    /** 올려 둔 파일의 id. */
    val fileId: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    /** 사진·동영상의 `"가로:세로"`. 말풍선 틀을 미리 잡는 데 쓴다. */
    val imageSize: String? = null,
    /** 공지 알림 — [NoticeAction] 값. */
    val noticeAction: String? = null,
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
        /** 이미 있던 대화가 바뀌었다(공감·회수). 바뀐 [message] 가 함께 온다. */
        const val TYPE_MESSAGE_UPDATED = "message_updated"
        /** 방의 공지가 바뀌었다. */
        const val TYPE_NOTICE = "notice"
    }
}

/** 방과 대화 요청에서 추가로 오는 [ApiError.code] 값. */
object ChatErrorCode {
    /** 그런 방이 없거나 내가 참여자가 아니다. 둘을 가르지 않는다 — 가르면 방이 있는지 알아낼 수 있다. */
    const val ROOM_NOT_FOUND = "room_not_found"
    const val USER_NOT_FOUND = "user_not_found"
    /** 그런 대화가 없거나, 그 대화에 할 수 없는 일이다(남의 대화 회수, 회수된 대화에 공감 등). */
    const val MESSAGE_NOT_FOUND = "message_not_found"
    /** 그런 파일이 없거나 내가 받을 수 없는 파일이다. */
    const val FILE_NOT_FOUND = "file_not_found"
    const val FILE_TOO_LARGE = "file_too_large"
}

/** 공감 한 칸 — 누가 어떤 반응을 눌렀는지. [kind] 는 `"0"`~`"5"` 다. */
@Serializable
data class ReactionDto(
    val userId: String,
    val userName: String = "",
    val kind: String,
)

/**
 * 공감 누르기.
 *
 * 켤지 끌지를 앱이 정해 보내지 않는다. 같은 [kind] 면 끄고 다르면 갈아타는 판단을 서버가 한다 —
 * 여러 기기가 각자 옛 상태를 근거로 계산하면 서로의 변경을 덮어쓴다.
 */
@Serializable
data class ToggleReactionRequest(val messageId: String, val kind: String)

@Serializable
data class RecallRequest(val messageId: String)

/** 공지 알림의 종류. */
object NoticeAction {
    const val ADD = "add"
    const val DELETE = "delete"
}

/** 방에 걸린 공지. 방마다 하나뿐이다. 공지가 없으면 [content] 가 비어서 온다. */
@Serializable
data class NoticeDto(
    val roomId: String,
    val id: String = "",
    val content: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val ownerPositionName: String = "",
    val createdAtEpochMillis: Long = 0,
)

/** 공지 등록. 이미 공지가 있으면 바꾼다. */
@Serializable
data class SetNoticeRequest(val content: String)

/** 공지를 등록하거나 지운 결과 — 바뀐 뒤의 공지와, 그 일을 알리는 대화. */
@Serializable
data class NoticeChangeResponse(
    val notice: NoticeDto,
    val message: MessageDto,
)

/** 책갈피 한 건. 내 것만 보인다. 대화 내용은 지금 시점의 것이다 — 회수됐으면 비어서 온다. */
@Serializable
data class BookmarkDto(
    val messageId: String,
    val content: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val sentAtEpochMillis: Long = 0,
)

@Serializable
data class BookmarksResponse(val bookmarks: List<BookmarkDto> = emptyList())

@Serializable
data class BookmarkRequest(val messageId: String)

/** 올린 파일. [id] 를 대화에 실어 보내면 방 참여자들이 내려받을 수 있다. */
@Serializable
data class FileDto(
    val id: String,
    val name: String,
    val size: Long,
)

/** 파일 한 개의 최대 크기. 앱과 서버가 같은 값을 본다. */
const val MAX_FILE_BYTES: Long = 20L * 1024 * 1024

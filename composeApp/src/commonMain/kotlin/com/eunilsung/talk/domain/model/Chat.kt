package com.eunilsung.talk.domain.model


/** 대화방 내부의 대화(메시지) 도메인. */
sealed class Chat {
    data class Item(
        val chatID: String = "",
        val chatType: String = "0",
        val title: String = "",
        val chatContent: String = "",
        val chatStatus: String = "0",
        val date: String = "",
        val unReadCount: String = "0",
        val empathy: EmpathyChat = EmpathyChat(),
        val user: User = User(),
        val replyChat: ReplyChat = ReplyChat(),
        val emoticon: Emoticon = Emoticon(),
        val vote: Vote? = null,
        val voteComplete: VoteComplete? = null,
        val imagePath: String = "",
        val imageSize: String = "",
        val originalFileName: String = "",
        val localPath: String = "",
        val isRecalled: Boolean = false,
        /** 파일 송신 중 업로드 진행률(0..100). -1 = 업로드 아님. 메모리 전용(DB 미저장). */
        val uploadProgress: Int = -1,
        /** 내가 보낸 대화인지. 로그인한 계정을 아는 쪽(매퍼·저장소)이 채운다. */
        val isMe: Boolean = false,
    )

    /** 대화 타입 비트마스크 (십진 문자열). */
    object Type {
        const val TEXT          = "0"
        const val IMAGE         = "1"
        const val FILE          = "2"
        const val VIDEO         = "4"
        const val ALARM         = "8"
        const val EMOTICON      = "32"
        const val VOICE         = "64"
        const val ADMIN         = "128"
        const val INVITE        = "256"
        const val EXIT          = "512"
        const val CONTACT       = "1024"
        const val VOTE          = "2048"
        const val VOTE_COMPLETE = "4096"
        const val RECALL        = "8192"
        const val EMPATHY       = "16384"
        const val MULTI_IMAGE   = "32768"
        const val REPLY         = "65536"
        const val DELETE        = "99999"
        const val CLEAR_CHAT    = "100000"
        const val ROOM_NAME     = "131072"
        const val NOTICE        = "262144"
    }

    /** 전송 상태. */
    object Status {
        const val SENDING           = "0"
        const val FAIL              = "1"
        const val COMPLETE          = "2"
        const val FILE_DOWNLOADING  = "3"

        /** 화면 표시 정렬 우선순위 — 작을수록 위. COMPLETE → SENDING → FAIL. */
        fun displayPriority(status: String): Int = when (status) {
            SENDING -> 1
            FAIL    -> 2
            else    -> 0
        }

        /** 리스트 하단 고정 그룹 키 — SENDING/FAIL 을 완료 대화 아래로 모은다. */
        fun bottomPinRank(status: String): Int = when (status) {
            SENDING, FAIL -> 1
            else          -> 0
        }
    }

    companion object {
        /** 입력창 최대 글자 수 */
        const val MAX_TEXT_LENGTH = 6000

        /** 화면 정렬용 표준 Comparator — 하단고정 → 날짜 ASC → 전송상태 순. */
        val SortByDateAndStatue: Comparator<Item> =
            compareBy<Item> { Status.bottomPinRank(it.chatStatus) }
                .thenBy { it.date }
                .thenBy { Status.displayPriority(it.chatStatus) }

        /** 첨부 파일 path 의 확장자/URI 패턴으로 chatType 분류. */
        fun typeFromPath(path: String): String {
            val ext = path.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) {
                if (ext in IMAGE_EXTENSIONS) return Type.IMAGE
                if (ext in VIDEO_EXTENSIONS) return Type.VIDEO
            }
            val lower = path.lowercase()
            if ("/media/external/images/" in lower || "/external/images/" in lower) return Type.IMAGE
            if ("/media/external/video/"  in lower || "/external/video/"  in lower) return Type.VIDEO
            return Type.FILE
        }

        /** 이미지 확장자 집합. */
        val IMAGE_EXTENSIONS: Set<String> = setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"
        )

        /** 동영상 확장자 집합. */
        val VIDEO_EXTENSIONS: Set<String> = setOf(
            "mp4", "mov", "avi", "mkv", "webm", "m4v", "3gp", "wmv", "flv"
        )
    }
}

/**
 * 다른 방으로 전달할 수 있는 대화인지 — 보내기가 끝났고 회수되지 않은 글·답장·이모티콘·사진·동영상·파일.
 * 투표·공지·초대/나감 같은 알림 대화는 전달하지 않는다. 첨부는 기기에 받아 둔 파일이 있어야 한다.
 */
val Chat.Item.isForwardable: Boolean
    get() = chatStatus == Chat.Status.COMPLETE && !isRecalled && when (chatType) {
        Chat.Type.TEXT, Chat.Type.REPLY -> chatContent.isNotBlank()
        Chat.Type.EMOTICON -> chatContent.isNotBlank() || emoticon.id.isNotBlank()
        Chat.Type.IMAGE, Chat.Type.VIDEO, Chat.Type.FILE -> imagePath.isNotBlank()
        else -> false
    }

/** 공감 — chatID 별로 이모지(0..5)를 누른 사용자. */
data class EmpathyChat(
    val chatID: String = "",
    val empathy0: List<User> = emptyList(),
    val empathy1: List<User> = emptyList(),
    val empathy2: List<User> = emptyList(),
    val empathy3: List<User> = emptyList(),
    val empathy4: List<User> = emptyList(),
    val empathy5: List<User> = emptyList(),
)

/** 답장 원문 — 인용 대상. */
data class ReplyChat(
    val chatID: String = "",
    val chatType: String = "0",
    val chatContent: String = "",
    val user: User = User(),
    val emoticon: Emoticon = Emoticon(),
    val imagePath: String = ""
)

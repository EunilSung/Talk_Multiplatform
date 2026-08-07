package com.eunilsung.talk.data.mapper

import com.eunilsung.talk.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.eunilsung.talk.db.ChatEntity
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.EmpathyChat
import com.eunilsung.talk.domain.model.Emoticon
import com.eunilsung.talk.domain.model.ReplyChat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.model.Vote
import com.eunilsung.talk.domain.model.VoteComplete
import com.eunilsung.talk.domain.model.VoteSetting
import com.eunilsung.talk.domain.model.VoteResultItem

/** Chat 도메인 <-> DB 매퍼. 복합 필드는 [Json] 으로 인코딩해 단일 TEXT 컬럼에 저장. */
class ChatMapper {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun toEntity(myId: String, chatRoomId: String, chat: Chat.Item): ChatEntity = ChatEntity(
        myId = myId,
        chatRoomId = chatRoomId,
        chatId = chat.chatID,
        chatType = chat.chatType,
        title = chat.title,
        chatContent = chat.chatContent,
        chatStatue = chat.chatStatue,
        date = chat.date,
        unReadCount = chat.unReadCount,
        imagePath = chat.imagePath,
        imageSize = chat.imageSize,
        originalFileName = chat.originalFileName,
        isRecalled = if (chat.isRecalled) "1" else "0",
        userJson = encodeUser(chat.user),
        empathyJson = encodeEmpathy(chat.empathy),
        replyChatJson = encodeReply(chat.replyChat),
        emoticonJson = encodeEmoticon(chat.emoticon),
        voteJson = encodeVote(chat.vote),
        voteCompleteJson = encodeVoteComplete(chat.voteComplete)
    )

    fun toModel(entity: ChatEntity): Chat.Item = Chat.Item(
        chatID = entity.chatId,
        chatType = entity.chatType,
        title = entity.title.orEmpty(),
        chatContent = entity.chatContent.orEmpty(),
        chatStatue = entity.chatStatue,
        date = entity.date.orEmpty(),
        unReadCount = entity.unReadCount ?: "0",
        empathy = decodeEmpathy(entity.empathyJson),
        user = decodeUser(entity.userJson),
        replyChat = decodeReply(entity.replyChatJson),
        emoticon = decodeEmoticon(entity.emoticonJson),
        vote = decodeVote(entity.voteJson),
        voteComplete = decodeVoteComplete(entity.voteCompleteJson),
        imagePath = entity.imagePath.orEmpty(),
        imageSize = entity.imageSize.orEmpty(),
        originalFileName = entity.originalFileName.orEmpty(),
        isRecalled = (entity.isRecalled ?: "0") == "1"
    )

    private fun encodeUser(u: User): String =
        runCatching { json.encodeToString(UserDto.serializer(), UserDto.from(u)) }
            .getOrElse { "{}" }

    private fun decodeUser(s: String?): User =
        runCatching {
            if (s.isNullOrBlank()) User()
            else json.decodeFromString(UserDto.serializer(), s).toUser()
        }.getOrElse { User() }

    private fun encodeEmpathy(e: EmpathyChat): String =
        runCatching { json.encodeToString(EmpathyDto.serializer(), EmpathyDto.from(e)) }
            .getOrElse { "{}" }

    /** [encodeEmpathy] 의 public wrapper — DB UPDATE 용 직렬화 문자열. */
    fun encodeEmpathyForUpdate(e: EmpathyChat): String = encodeEmpathy(e)

    private fun decodeEmpathy(s: String?): EmpathyChat =
        runCatching {
            if (s.isNullOrBlank()) EmpathyChat()
            else json.decodeFromString(EmpathyDto.serializer(), s).toEmpathy()
        }.getOrElse { EmpathyChat() }

    private fun encodeReply(r: ReplyChat): String =
        runCatching { json.encodeToString(ReplyDto.serializer(), ReplyDto.from(r)) }
            .getOrElse { "{}" }

    private fun decodeReply(s: String?): ReplyChat =
        decodeOr("reply", ReplyChat()) {
            if (s.isNullOrBlank()) ReplyChat()
            else json.decodeFromString(ReplyDto.serializer(), s).toReply()
        }

    private fun encodeEmoticon(e: Emoticon): String =
        runCatching { json.encodeToString(EmoticonDto.serializer(), EmoticonDto.from(e)) }
            .getOrElse { "{}" }

    private fun decodeEmoticon(s: String?): Emoticon =
        decodeOr("emoticon", Emoticon()) {
            if (s.isNullOrBlank()) Emoticon()
            else json.decodeFromString(EmoticonDto.serializer(), s).toEmoticon()
        }

    private fun encodeVote(v: Vote?): String? =
        runCatching { if (v == null) null else json.encodeToString(VoteDto.serializer(), VoteDto.from(v)) }
            .getOrNull()

    private fun decodeVote(s: String?): Vote? =
        decodeOr("vote", null) {
            if (s.isNullOrBlank()) null
            else json.decodeFromString(VoteDto.serializer(), s).toVote()
        }

    private fun encodeVoteComplete(v: VoteComplete?): String? =
        runCatching { if (v == null) null else json.encodeToString(VoteCompleteDto.serializer(), VoteCompleteDto.from(v)) }
            .getOrNull()

    private fun decodeVoteComplete(s: String?): VoteComplete? =
        decodeOr("voteComplete", null) {
            if (s.isNullOrBlank()) null
            else json.decodeFromString(VoteCompleteDto.serializer(), s).toVoteComplete()
        }

    /**
     * 디코드 실패를 조용히 삼키지 않고 남긴다.
     *
     * 실패하면 해당 항목만 비므로 화면에서는 "투표 선택지가 안 보인다" 처럼만 드러나
     * 로그가 없으면 원인을 찾기 어렵다.
     */
    private inline fun <T> decodeOr(tag: String, fallback: T, block: () -> T): T =
        runCatching(block).getOrElse {
            Log.message("[ChatMapper] $tag decode failed: ${it.message}")
            fallback
        }

    @Serializable
    private data class UserDto(
        val id: String = "",
        val name: String = "",
        val departmentName: String? = null,
        val positionName: String? = null,
        val nickname: String? = null
    ) {
        fun toUser() = User(
            id = id,
            name = name,
            departmentName = departmentName,
            positionName = positionName,
            nickname = nickname
        )
        companion object {
            fun from(u: User) = UserDto(
                id = u.id,
                name = u.name,
                departmentName = u.departmentName,
                positionName = u.positionName,
                nickname = u.nickname
            )
        }
    }

    @Serializable
    private data class EmpathyDto(
        val chatID: String = "",
        val empathy0: List<UserDto> = emptyList(),
        val empathy1: List<UserDto> = emptyList(),
        val empathy2: List<UserDto> = emptyList(),
        val empathy3: List<UserDto> = emptyList(),
        val empathy4: List<UserDto> = emptyList(),
        val empathy5: List<UserDto> = emptyList()
    ) {
        fun toEmpathy() = EmpathyChat(
            chatID = chatID,
            empathy0 = empathy0.map { it.toUser() },
            empathy1 = empathy1.map { it.toUser() },
            empathy2 = empathy2.map { it.toUser() },
            empathy3 = empathy3.map { it.toUser() },
            empathy4 = empathy4.map { it.toUser() },
            empathy5 = empathy5.map { it.toUser() }
        )
        companion object {
            fun from(e: EmpathyChat) = EmpathyDto(
                chatID = e.chatID,
                empathy0 = e.empathy0.map { UserDto.from(it) },
                empathy1 = e.empathy1.map { UserDto.from(it) },
                empathy2 = e.empathy2.map { UserDto.from(it) },
                empathy3 = e.empathy3.map { UserDto.from(it) },
                empathy4 = e.empathy4.map { UserDto.from(it) },
                empathy5 = e.empathy5.map { UserDto.from(it) }
            )
        }
    }

    @Serializable
    private data class ReplyDto(
        val chatID: String = "",
        val chatType: String = "0",
        val chatContent: String = "",
        val user: UserDto = UserDto(),
        val emoticon: EmoticonDto = EmoticonDto(),
        val imagePath: String = ""
    ) {
        fun toReply() = ReplyChat(
            chatID = chatID,
            chatType = chatType,
            chatContent = chatContent,
            user = user.toUser(),
            emoticon = emoticon.toEmoticon(),
            imagePath = imagePath
        )
        companion object {
            fun from(r: ReplyChat) = ReplyDto(
                chatID = r.chatID,
                chatType = r.chatType,
                chatContent = r.chatContent,
                user = UserDto.from(r.user),
                emoticon = EmoticonDto.from(r.emoticon),
                imagePath = r.imagePath
            )
        }
    }

    @Serializable
    private data class EmoticonDto(
        val id: String = "",
        val type: Int = 0
    ) {
        fun toEmoticon() = Emoticon(id = id, type = type)
        companion object {
            fun from(e: Emoticon) = EmoticonDto(id = e.id, type = e.type)
        }
    }

    @Serializable
    private data class VoteDto(
        val id: String = "",
        val items: List<String> = emptyList(),
        val itemType: String = "TEXT",
        val setting: VoteSettingDto = VoteSettingDto()
    ) {
        fun toVote() = Vote(
            id = id,
            items = items,
            itemType = itemType,
            setting = setting.toSetting()
        )
        companion object {
            fun from(v: Vote) = VoteDto(
                id = v.id,
                items = v.items,
                itemType = v.itemType,
                setting = VoteSettingDto.from(v.setting)
            )
        }
    }

    @Serializable
    private data class VoteSettingDto(
        val settingEndTime: Boolean = false,
        val endTime: String = "",
        val multiSelect: Boolean = false,
        val anonymous: Boolean = false,
        val allowAddItem: Boolean = false
    ) {
        fun toSetting() = VoteSetting(
            settingEndTime = settingEndTime,
            endTime = endTime,
            multiSelect = multiSelect,
            anonymous = anonymous,
            allowAddItem = allowAddItem
        )
        companion object {
            fun from(s: VoteSetting) = VoteSettingDto(
                settingEndTime = s.settingEndTime,
                endTime = s.endTime,
                multiSelect = s.multiSelect,
                anonymous = s.anonymous,
                allowAddItem = s.allowAddItem
            )
        }
    }

    @Serializable
    private data class VoteCompleteDto(
        val id: String = "",
        val items: List<VoteResultItemDto> = emptyList()
    ) {
        fun toVoteComplete() = VoteComplete(
            id = id,
            items = items.map { it.toItem() }
        )
        companion object {
            fun from(v: VoteComplete) = VoteCompleteDto(
                id = v.id,
                items = v.items.map { VoteResultItemDto.from(it) }
            )
        }
    }

    @Serializable
    private data class VoteResultItemDto(
        val idx: Int = 0,
        val seq: Int = 0,
        val content: String = "",
        val nVote: Int = 0,
        val writeUserId: String = ""
    ) {
        fun toItem() = VoteResultItem(
            idx = idx,
            seq = seq,
            content = content,
            nVote = nVote,
            writeUserId = writeUserId
        )
        companion object {
            fun from(i: VoteResultItem) = VoteResultItemDto(
                idx = i.idx,
                seq = i.seq,
                content = i.content,
                nVote = i.nVote,
                writeUserId = i.writeUserId
            )
        }
    }
}

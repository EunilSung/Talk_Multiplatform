package com.eunilsung.talk.data.mapper

import kotlinx.serialization.json.Json
import com.eunilsung.talk.data.local.LocalSecret
import com.eunilsung.talk.db.ChatRoomEntity
import com.eunilsung.talk.domain.model.ChatRoom

class ChatRoomMapper {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun toEntity(myId: String, model: ChatRoom.Item): ChatRoomEntity {
        return ChatRoomEntity(
            myId = myId,
            roomId = model.id,
            title = LocalSecret.encrypt(model.title),
            unReadCount = model.unReadCount,
            isAlarm = model.isAlarm,
            firstChatId = model.firstChatID,
            lastChatDate = model.lastChatDate,
            lastChatId = model.lastChatID,
            lastChatContent = LocalSecret.encrypt(model.lastChatContent),
            totalUserList = LocalSecret.encrypt(model.totalUserList),
            totalUserCount = model.totalUserCount,
            exitUserList = LocalSecret.encrypt(model.exitUserList),
            exitUserCount = model.exitUserCount,
            pinDate = model.pinDate,
            mentionCount = model.mentionCount,
            orgMessageId = model.orgMessageId,
            clsUsr = LocalSecret.encrypt(model.clsUsr),
            enableMode = model.enableMode
        )
    }

    fun toModel(entity: ChatRoomEntity): ChatRoom.Item {
        return ChatRoom.Item(
            id = entity.roomId,
            title = LocalSecret.decrypt(entity.title ?: ""),
            unReadCount = entity.unReadCount ?: "",
            isAlarm = entity.isAlarm ?: "0",
            firstChatID = entity.firstChatId ?: "",
            lastChatDate = entity.lastChatDate ?: "",
            lastChatID = entity.lastChatId ?: "",
            lastChatContent = LocalSecret.decrypt(entity.lastChatContent ?: ""),
            totalUserList = LocalSecret.decrypt(entity.totalUserList ?: ""),
            totalUserCount = entity.totalUserCount ?: "",
            exitUserList = LocalSecret.decrypt(entity.exitUserList ?: ""),
            exitUserCount = entity.exitUserCount ?: "",
            pinDate = entity.pinDate ?: "",
            mentionCount = entity.mentionCount ?: "",
            orgMessageId = entity.orgMessageId ?: "",
            clsUsr = LocalSecret.decrypt(entity.clsUsr ?: ""),
            enableMode = entity.enableMode ?: ""
        )
    }
}

package com.eunilsung.talk.ui.chatroom

import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.GroupedChat

fun groupChatsForUi(
    chats: List<Chat.Item>,
    firstUnreadChatId: String? = null,
    matchedChatIds: Set<String> = emptySet(),
    currentMatchChatId: String? = null,
    focusChatId: String? = null
): List<GroupedChat> {
    if (chats.isEmpty()) return emptyList()

    val times = chats.map { parseChatTime(it.date) }

    return chats.mapIndexed { i, current ->
        val curT  = times[i]
        val next  = chats.getOrNull(i + 1)
        val nextT = times.getOrNull(i + 1)
        val prev  = chats.getOrNull(i - 1)
        val prevT = times.getOrNull(i - 1)

        val nextIsSystem = next?.chatType == Chat.Type.INVITE || next?.chatType == Chat.Type.EXIT
        val prevIsSystem = prev?.chatType == Chat.Type.INVITE || prev?.chatType == Chat.Type.EXIT

        val showProfileAndName = next == null ||
                nextIsSystem ||
                current.user.id != next.user.id ||
                !sameMinute(curT, nextT) ||
                !sameDay(curT, nextT)

        val showTime = prev == null ||
                prevIsSystem ||
                current.user.id != prev.user.id ||
                !sameMinute(curT, prevT) ||
                !sameDay(curT, prevT)

        val showDate = next == null || !sameDay(curT, nextT)

        val showUnreadMarker =
            firstUnreadChatId != null && current.chatID == firstUnreadChatId

        val isSearchMatch = current.chatID in matchedChatIds
        val isCurrentSearchMatch = current.chatID == currentMatchChatId
        val isFocused = current.chatID == focusChatId

        GroupedChat(
            chat = current,
            showProfileAndName = showProfileAndName,
            showDate = showDate,
            showTime = showTime,
            showUnreadMarker = showUnreadMarker,
            isSearchMatch = isSearchMatch,
            isCurrentSearchMatch = isCurrentSearchMatch,
            isFocused = isFocused
        )
    }
}


private data class ChatTime(
    val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int
)

private val INVALID = ChatTime(-1, -1, -1, -1, -1)

private fun parseChatTime(date: String): ChatTime {
    if (date.isBlank()) return INVALID
    val parts = date.split(" ")
    if (parts.size != 2) return INVALID
    val d = parts[0].split("-")
    val t = parts[1].split(":")
    if (d.size != 3 || t.size < 2) return INVALID
    val year = d[0].toIntOrNull() ?: return INVALID
    val month = d[1].toIntOrNull() ?: return INVALID
    val day = d[2].toIntOrNull() ?: return INVALID
    val hour = t[0].toIntOrNull() ?: return INVALID
    val minute = t[1].toIntOrNull() ?: return INVALID
    return ChatTime(year, month, day, hour, minute)
}

private fun sameDay(a: ChatTime?, b: ChatTime?): Boolean =
    a != null && b != null &&
            a != INVALID && b != INVALID &&
            a.year == b.year && a.month == b.month && a.day == b.day

private fun sameMinute(a: ChatTime?, b: ChatTime?): Boolean =
    a != null && b != null &&
            a != INVALID && b != INVALID &&
            a.hour == b.hour && a.minute == b.minute

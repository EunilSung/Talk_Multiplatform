package com.eunilsung.talk.domain.model

import com.eunilsung.talk.util.UserListCodec

sealed class ChatRoom{
    data class Item(
        val id: String = "",
        val title: String = "",
        val unReadCount: String = "",
        val isAlarm: String = "",
        val firstChatID: String = "",
        val lastChatDate: String = "",
        val lastChatID: String = "",
        val lastChatContent: String = "",
        val totalUserList: String = "",
        val totalUserCount: String = "",
        val exitUserList: String = "",
        val exitUserCount: String = "",
        val pinDate: String = "",
        val mentionCount: String = "" ,
        val orgMessageId: String = "",
        val clsUsr: String = "",
        val enableMode: String = "",
        /** 이 목록을 보는 사람(로그인한 계정)의 id. 상대 이름·프로필을 고를 때 나를 빼는 데 쓴다. 매퍼가 채운다. */
        val myId: String = "",
    ) {
        val displayUserCount: Int
            get() {
                val total = totalUserCount.toIntOrNull() ?: 0
                val exit = exitUserCount.toIntOrNull() ?: 0
                return total - exit
            }

        /** 처음부터 본인 1명만 있던 방 (자기 자신과의 채팅) */
        val isMyChatRoom: Boolean
            get() = totalUserCount == "1" && exitUserCount == "0"

        /** 본인만 남은 방(내 대화방 제외). */
        val isAbandonedRoom: Boolean
            get() = !isMyChatRoom && displayUserCount == 1

        /** 1:1 대화방(초기 총 2명). 상대 퇴장 여부와 무관. */
        val isDirectRoom: Boolean
            get() = totalUserCount == "2"

        /** 1:1 방의 상대 (id, name). 활성 목록에 없으면 퇴장자 목록에서 찾는다. */
        private val directPartner: Pair<String, String>?
            get() {
                if (!isDirectRoom) return null
                val me = myId
                return UserListCodec.decode(totalUserList).firstOrNull { it.first != me }
                    ?: UserListCodec.decode(exitUserList).firstOrNull { it.first != me }
            }

        /** 1:1 방에서 상대가 퇴장한 경우의 상대 (id, name), 아니면 null. */
        val abandonedDirectPartner: Pair<String, String>?
            get() = if (isDirectRoom && isAbandonedRoom) directPartner else null

        val displayTitle: String
            get() = when {
                isAbandonedRoom && !isDirectRoom -> ""
                isDirectRoom -> directPartner?.second?.takeIf { it.isNotBlank() } ?: title
                else -> title
            }

        val displayProfileIds: List<String>
            get() {
                if (isAbandonedRoom && !isDirectRoom) return emptyList()

                val me = myId
                val activeIds = UserListCodec.decode(totalUserList).map { it.first }
                val others = activeIds.filter { it != me }.take(4)
                if (others.isNotEmpty()) return others
                directPartner?.let { return listOf(it.first) }
                return activeIds.take(1)
            }
    }

}

package com.eunilsung.talk.domain.model

import com.eunilsung.talk.domain.util.UserListCodec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 방 찾기는 화면에 보이는 이름으로 한다 — 둘만의 방은 서버 제목이 비어 있고 상대 이름이 보인다. */
class ChatRoomTitleTest {

    private val direct = ChatRoom.Item(
        id = "r1",
        myId = "test1",
        title = "",
        totalUserList = UserListCodec.encode(listOf("test1" to "김민준", "test10" to "한지호")),
        totalUserCount = "2",
        exitUserCount = "0",
    )

    @Test
    fun 둘만의_방은_상대_이름으로_찾힌다() {
        assertTrue(direct.matchesTitle("한지호"))
        assertTrue(direct.matchesTitle("지호"))
        assertFalse(direct.matchesTitle("김민준"))
    }

    @Test
    fun 안읽음은_숫자로_본다() {
        assertTrue(direct.copy(unReadCount = "3").hasUnread)
        assertFalse(direct.copy(unReadCount = "0").hasUnread)
        assertFalse(direct.copy(unReadCount = "").hasUnread)
    }

    @Test
    fun 알림_끔과_알림방을_알아본다() {
        assertTrue(direct.copy(isAlarm = ALARM_OFF).isMuted)
        assertFalse(direct.copy(isAlarm = ALARM_ON).isMuted)
        assertTrue(direct.copy(id = "noti\$1").isNotificationRoom)
        assertFalse(direct.isNotificationRoom)
    }
}

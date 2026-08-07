package com.eunilsung.talk.data.testdata

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.eunilsung.talk.domain.model.ChatGroup
import com.eunilsung.talk.util.UserListCodec
import com.eunilsung.talk.domain.model.ChatRoom

/** 로컬 테스트용 대화방 목록 구성. */
object TestChatRooms {

    /** myId 는 로그인한 본인 — 참여자 목록에 항상 포함. */
    /** 안읽음·멘션 수는 [TestChats] 스크립트에서 도출한다 — 목록과 방 안이 어긋나지 않게. */
    fun seed(myId: String): List<ChatRoom.Item> =
        buildRooms(myId).map { room ->
            room.copy(
                unReadCount = TestChats.unreadCountFor(room, myId).toString(),
                mentionCount = TestChats.mentionCountFor(room, myId).toString(),
            )
        }

    private fun buildRooms(myId: String): List<ChatRoom.Item> {
        val me = myId to nameOf(myId)

        // 본인이 상대로 지정된 경우를 피하려고, 본인이 아닌 계정 중에서 상대를 고른다.
        val others = TestAccounts.ALL.map { it.userId }.filterNot { it.equals(myId, ignoreCase = true) }
        fun partner(preferred: String): String =
            if (preferred.equals(myId, ignoreCase = true)) others.first() else preferred

        val p2 = partner("test2")
        val p5 = partner("test5")
        val p9 = partner("test9")

        return listOf(
            direct(
                id = "room_direct_1",
                me = me,
                partnerId = p2,
                lastChat = "네 확인했습니다. 오후에 다시 공유드릴게요.",
                minutesAgo = 4,
            ),
            group(
                id = "room_group_release",
                title = "9월 릴리즈 준비",
                me = me,
                memberIds = listOf("test2", "test3", "test4", "test5", "test6")
                    .map { partner(it) }.distinct(),
                lastChat = "그럼 내일 오전에 최종 확인하고 배포하겠습니다",
                minutesAgo = 25,
            ),
            group(
                id = "room_group_dev",
                title = "개발팀 회의",
                me = me,
                memberIds = listOf("test2", "test3", "test4").map { partner(it) }.distinct(),
                lastChat = "빌드 통과했습니다 👍",
                minutesAgo = 95,
                pinned = true,
            ),
            group(
                id = "room_group_plan",
                title = "기획·디자인 협업",
                me = me,
                memberIds = listOf("test5", "test6", "test7", "test8").map { partner(it) }.distinct(),
                lastChat = "@${nameOf(myId)} 시안 확인 부탁드립니다",
                minutesAgo = 260,
            ),
            direct(
                id = "room_direct_2",
                me = me,
                partnerId = p5,
                lastChat = "회의록 정리해서 올려두었습니다.",
                minutesAgo = 60 * 26,          // 어제
                alarmOff = true,
            ),
            selfRoom(
                id = "room_self",
                me = me,
                lastChat = "메모: 배포 전 체크리스트 확인",
                minutesAgo = 60 * 30,          // 어제
            ),
            direct(
                id = "room_direct_3",
                me = me,
                partnerId = p9,
                lastChat = "감사합니다. 좋은 하루 보내세요.",
                minutesAgo = 60 * 24 * 3,      // 3일 전
            ),
            abandonedDirect(
                id = "room_direct_left",
                me = me,
                partnerId = partner("test10"),
                lastChat = "상대방이 대화방을 나갔습니다.",
                minutesAgo = 60 * 24 * 8,      // 8일 전
            ),
        )
    }

    /** 대화방 그룹 칩 — kind "2" 는 사용자 커스텀 그룹. */
    fun seedGroups(): List<ChatGroup> = listOf(
        ChatGroup(
            id = "cg_work",
            name = "업무",
            kind = "2",
            sort = "1",
            roomIds = listOf("room_group_dev", "room_group_plan"),
        ),
        ChatGroup(
            id = "cg_personal",
            name = "개인",
            kind = "2",
            sort = "2",
            roomIds = listOf("room_direct_1", "room_direct_2"),
        ),
    )

    // ---- 방 종류별 생성 ----

    private fun direct(
        id: String,
        me: Pair<String, String>,
        partnerId: String,
        lastChat: String,
        minutesAgo: Long,
        unRead: String = "0",
        alarmOff: Boolean = false,
    ) = base(
        id = id,
        title = nameOf(partnerId),
        users = listOf(me, partnerId to nameOf(partnerId)),
        lastChat = lastChat,
        minutesAgo = minutesAgo,
        unRead = unRead,
        isAlarm = if (alarmOff) "1" else "0",
    )

    private fun group(
        id: String,
        title: String,
        me: Pair<String, String>,
        memberIds: List<String>,
        lastChat: String,
        minutesAgo: Long,
        unRead: String = "0",
        pinned: Boolean = false,
        mention: String = "0",
    ) = base(
        id = id,
        title = title,
        users = listOf(me) + memberIds.map { it to nameOf(it) },
        lastChat = lastChat,
        minutesAgo = minutesAgo,
        unRead = unRead,
        mentionCount = mention,
        // 상단고정은 서버가 아니라 로컬 Settings 소관이라, 시드에서는 정렬용 값만 넣어둔다.
        pinDate = if (pinned) PIN_SEED_MILLIS else "",
    )

    private fun selfRoom(
        id: String,
        me: Pair<String, String>,
        lastChat: String,
        minutesAgo: Long,
    ) = base(
        id = id,
        title = me.second,
        users = listOf(me),
        lastChat = lastChat,
        minutesAgo = minutesAgo,
        unRead = "0",
    )

    /** 1:1 인데 상대가 퇴장한 방 — 활성 목록에서 빠지고 퇴장자 목록에 남는다. */
    private fun abandonedDirect(
        id: String,
        me: Pair<String, String>,
        partnerId: String,
        lastChat: String,
        minutesAgo: Long,
    ) = ChatRoom.Item(
        id = id,
        title = nameOf(partnerId),
        unReadCount = "0",
        isAlarm = "0",
        lastChatDate = timestamp(minutesAgo),
        lastChatContent = lastChat,
        totalUserList = UserListCodec.encode(listOf(me)),
        totalUserCount = "2",
        exitUserList = UserListCodec.encode(listOf(partnerId to nameOf(partnerId))),
        exitUserCount = "1",
        enableMode = "0",
    )

    private fun base(
        id: String,
        title: String,
        users: List<Pair<String, String>>,
        lastChat: String,
        minutesAgo: Long,
        unRead: String = "0",
        isAlarm: String = "0",
        mentionCount: String = "0",
        pinDate: String = "",
    ) = ChatRoom.Item(
        id = id,
        title = title,
        unReadCount = unRead,
        isAlarm = isAlarm,
        lastChatDate = timestamp(minutesAgo),
        lastChatContent = lastChat,
        totalUserList = UserListCodec.encode(users),
        totalUserCount = users.size.toString(),
        exitUserList = "",
        exitUserCount = "0",
        pinDate = pinDate,
        mentionCount = mentionCount,
        enableMode = "0",
    )

    // ---- 유틸 ----

    private fun nameOf(userId: String): String =
        TestAccounts.find(userId)?.userName ?: userId


    /** `DateUtils.convertChatRoomDate` 가 파싱하는 `yyyy-MM-dd HH:mm:ss`. */
    private fun timestamp(minutesAgo: Long): String {
        val millis = kotlin.time.Clock.System.now().toEpochMilliseconds() - minutesAgo * 60_000L
        val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val y = dt.year.toString().padStart(4, '0')
        val mo = dt.monthNumber.toString().padStart(2, '0')
        val d = dt.dayOfMonth.toString().padStart(2, '0')
        val h = dt.hour.toString().padStart(2, '0')
        val mi = dt.minute.toString().padStart(2, '0')
        val s = dt.second.toString().padStart(2, '0')
        return "$y-$mo-$d $h:$mi:$s"
    }

    /** 시드 고정방의 pinDate — 사용자가 직접 고정한 방(현재시각)보다 항상 아래로 가도록 작은 값. */
    private const val PIN_SEED_MILLIS = "1"
}

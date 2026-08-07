package com.eunilsung.talk.data.sample

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.util.UserListCodec
import com.eunilsung.talk.domain.model.User

/** 대화방별 초기 대화 내용. */
object TestChats {

    /** 대화 간격 — 마지막 대화부터 이 간격만큼 거슬러 올라가며 배치. */
    private const val GAP_MINUTES = 7L

    fun seed(room: ChatRoom.Item, myId: String): List<Chat.Item> {
        val members = UserListCodec.decode(room.totalUserList)
        if (members.isEmpty()) return emptyList()

        val myName = TestAccounts.find(myId)?.userName ?: myId
        val others = members.filterNot { it.first.equals(myId, ignoreCase = true) }
        val lines = scriptFor(room, others.map { it.second }, myName)
        if (lines.isEmpty()) return emptyList()

        val lastMillis = parseMillis(room.lastChatDate) ?: return emptyList()

        // 마지막 줄이 방의 lastChatDate 와 같은 시각이 되도록 역순으로 시간을 매긴다.
        return lines.mapIndexed { index, line ->
            val fromEnd = lines.lastIndex - index
            val millis = lastMillis - fromEnd * GAP_MINUTES * 60_000L
            val speaker =
                if (line.mine || others.isEmpty()) myId to myName
                else others[line.speakerIndex % others.size]

            Chat.Item(
                chatID = chatIdAt(millis, speaker.first, index),
                chatType = Chat.Type.TEXT,
                chatContent = line.text,
                chatStatue = Chat.Statue.COMPLETE,
                date = formatChatDate(millis),
                unReadCount = "0",
                user = User(id = speaker.first, name = speaker.second),
            )
        }
    }

    /**
     * 방 시드에 넣을 안읽음 수 — 마지막으로 내가 보낸 뒤 상대가 보낸 대화 수.
     *
     * 하드코딩하면 실제 대화 수와 어긋나(리스트엔 12, 방엔 5) 시연에서 바로 들통난다.
     * 스크립트 하나를 단일 출처로 두고 여기서 세어 낸다.
     */
    fun unreadCountFor(room: ChatRoom.Item, myId: String): Int =
        trailingFromOthers(room, myId).size

    /** 안읽음 중 나를 멘션한 대화 수. */
    fun mentionCountFor(room: ChatRoom.Item, myId: String): Int {
        val myName = TestAccounts.find(myId)?.userName ?: myId
        return trailingFromOthers(room, myId).count { it.text.contains("@$myName") }
    }

    /** 마지막 내 대화 이후의 상대 대화들. 내 대화가 없으면 전부. */
    private fun trailingFromOthers(room: ChatRoom.Item, myId: String): List<Line> {
        val members = UserListCodec.decode(room.totalUserList)
        val myName = TestAccounts.find(myId)?.userName ?: myId
        val others = members.filterNot { it.first.equals(myId, ignoreCase = true) }
        // 상대가 모두 퇴장한 방은 [seed] 가 화자를 전부 나로 배정하므로 안읽음이 없다.
        if (others.isEmpty()) return emptyList()
        val lines = scriptFor(room, others.map { it.second }, myName)
        return lines.takeLastWhile { !it.mine }
    }

    // ---- 방별 대화 스크립트 ----

    private data class Line(val text: String, val mine: Boolean, val speakerIndex: Int = 0)

    private fun scriptFor(
        room: ChatRoom.Item,
        otherNames: List<String>,
        myName: String,
    ): List<Line> {
        val first = otherNames.firstOrNull().orEmpty()
        return when {
            room.isMyChatRoom -> listOf(
                Line("배포 전 체크리스트", mine = true),
                Line("1. 버전 코드 올리기\n2. 릴리즈 노트 정리\n3. 스토어 스크린샷 교체", mine = true),
                Line("메모: 배포 전 체크리스트 확인", mine = true),
            )

            room.id == "room_group_dev" -> listOf(
                Line("오늘 스프린트 리뷰 3시에 시작합니다", mine = true),
                Line("네 준비하겠습니다", mine = false, speakerIndex = 0),
                Line("저는 로그인 쪽 작업 공유드릴게요", mine = false, speakerIndex = 1),
                Line("좋습니다. 빌드도 한번 돌려주세요", mine = true),
                Line("빌드 통과했습니다 👍", mine = false, speakerIndex = 0),
            )

            // 스크롤·페이징 확인용 긴 방 — 총 35줄, 마지막 10줄이 상대 대화라 안읽음 10.
            room.id == "room_group_release" -> listOf(
                Line("9월 릴리즈 준비 시작하겠습니다", mine = true),
                Line("일정 공유 부탁드려요", mine = false, speakerIndex = 0),
                Line("QA는 다음 주 화요일부터 들어갑니다", mine = true),
                Line("그럼 코드 프리즈는 월요일 저녁으로 볼까요?", mine = false, speakerIndex = 1),
                Line("네 그렇게 잡겠습니다", mine = true),
                Line("디자인 QA도 같이 태워야 할 것 같은데요", mine = false, speakerIndex = 2),
                Line("시안 반영분이 아직 두 건 남았습니다", mine = false, speakerIndex = 3),
                Line("그건 이번 주 안에 정리될 예정입니다", mine = true),
                Line("푸시 알림 문구는 확정됐나요?", mine = false, speakerIndex = 4),
                Line("초안은 나왔고 검토 중입니다", mine = true),
                Line("스토어 스크린샷도 새로 찍어야 합니다", mine = false, speakerIndex = 0),
                Line("기기별로 세 종류면 될까요?", mine = false, speakerIndex = 1),
                Line("폰·폴드·태블릿 세 종류로 하시죠", mine = true),
                Line("릴리즈 노트 초안 올려두었습니다", mine = false, speakerIndex = 2),
                Line("확인했습니다. 문구만 조금 다듬을게요", mine = true),
                Line("버전 코드는 얼마로 올리나요?", mine = false, speakerIndex = 3),
                Line("1010 으로 갑니다", mine = true),
                Line("빌드 스크립트에 반영해두겠습니다", mine = false, speakerIndex = 4),
                Line("크래시 리포트 연동도 확인 부탁드려요", mine = true),
                Line("어제 테스트했고 정상 수집됩니다", mine = false, speakerIndex = 0),
                Line("좋습니다. 그럼 남은 건 QA뿐이네요", mine = true),
                Line("테스트 케이스 정리해서 공유드릴게요", mine = false, speakerIndex = 1),
                Line("감사합니다", mine = true),
                Line("공유드렸습니다. 확인 부탁드립니다", mine = false, speakerIndex = 1),
                Line("잘 봤습니다. 이대로 진행하시죠", mine = true),
                Line("QA 환경 세팅도 끝났습니다", mine = false, speakerIndex = 3),
                Line("QA 1일차 결과 정리해서 올립니다", mine = false, speakerIndex = 0),
                Line("치명 이슈는 없고 사소한 것 세 건입니다", mine = false, speakerIndex = 0),
                Line("첫 번째는 다크모드에서 카드 배경이 밝게 남는 건입니다", mine = false, speakerIndex = 2),
                Line("두 번째는 긴 공지 본문이 잘리지 않는 건이고요", mine = false, speakerIndex = 2),
                Line("세 번째는 갤러리 앨범 필터가 느리게 뜨는 건입니다", mine = false, speakerIndex = 3),
                Line("세 건 다 오늘 중에 수정 가능해 보입니다", mine = false, speakerIndex = 1),
                Line("수정되면 바로 재검증하겠습니다", mine = false, speakerIndex = 0),
                Line("빌드 올라오면 알려주세요", mine = false, speakerIndex = 4),
                Line("그럼 내일 오전에 최종 확인하고 배포하겠습니다", mine = false, speakerIndex = 1),
            )

            room.id == "room_group_plan" -> listOf(
                Line("이번 주 시안 공유드립니다", mine = false, speakerIndex = 0),
                Line("확인했습니다. 컬러는 이대로 갈까요?", mine = true),
                Line("메인 톤만 조금 낮추면 좋을 것 같아요", mine = false, speakerIndex = 1),
                Line("반영해서 다시 올리겠습니다", mine = false, speakerIndex = 0),
                Line("@$myName 시안 확인 부탁드립니다", mine = false, speakerIndex = 1),
            )

            room.isDirectRoom -> listOf(
                Line("$first 님, 잠시 통화 가능하실까요?", mine = true),
                Line("지금은 회의 중이라 30분 뒤에 괜찮습니다", mine = false),
                Line("네 그때 연락드릴게요", mine = true),
                Line(room.lastChatContent.ifBlank { "확인했습니다." }, mine = false),
            )

            else -> listOf(
                Line(room.lastChatContent.ifBlank { "대화를 시작해 보세요." }, mine = false)
            )
        }
    }

    // ---- 유틸 ----


    /** `yyyy-MM-dd HH:mm:ss` → epoch millis. 파싱 실패 시 null. */
    private fun parseMillis(date: String): Long? {
        val parts = date.split(" ")
        if (parts.size != 2) return null
        val d = parts[0].split("-").mapNotNull { it.toIntOrNull() }
        val t = parts[1].split(":").mapNotNull { it.toIntOrNull() }
        if (d.size != 3 || t.size < 2) return null
        return runCatching {
            LocalDateTime(d[0], d[1], d[2], t[0], t[1], t.getOrElse(2) { 0 })
                .toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
        }.getOrNull()
    }

    /** `DateUtils.convertChatDate` 가 파싱하는 `yyyy-MM-dd HH:mm:ss:SSS`. */
    private fun formatChatDate(millis: Long): String {
        val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        val y = dt.year.toString().padStart(4, '0')
        val mo = dt.monthNumber.toString().padStart(2, '0')
        val d = dt.dayOfMonth.toString().padStart(2, '0')
        val h = dt.hour.toString().padStart(2, '0')
        val mi = dt.minute.toString().padStart(2, '0')
        val s = dt.second.toString().padStart(2, '0')
        return "$y-$mo-$d $h:$mi:$s:000"
    }

    /** chatId 포맷 `yyyyMMddHHmmssSSS.userId` (정렬 키). */
    private fun chatIdAt(millis: Long, userId: String, seq: Int): String {
        val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        return buildString {
            append(dt.year.toString().padStart(4, '0'))
            append(dt.monthNumber.toString().padStart(2, '0'))
            append(dt.dayOfMonth.toString().padStart(2, '0'))
            append(dt.hour.toString().padStart(2, '0'))
            append(dt.minute.toString().padStart(2, '0'))
            append(dt.second.toString().padStart(2, '0'))
            append(seq.toString().padStart(3, '0'))
            append('.')
            append(userId)
        }
    }
}

package com.eunilsung.talk.data.local.seed

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.Emoticon
import com.eunilsung.talk.domain.model.EmpathyChat
import com.eunilsung.talk.domain.model.ReplyChat
import com.eunilsung.talk.domain.util.UserListCodec
import com.eunilsung.talk.domain.model.User
import multiplatformtalk.composeapp.generated.resources.Res

/** 대화방별 초기 대화 내용. */
object TestChats {

    /** 대화 간격 — 마지막 대화부터 이 간격만큼 거슬러 올라가며 배치. */
    private const val GAP_MINUTES = 7L

    /** 시안 공유 대화에 쓰는 사진 — 앱의 내 그룹 화면을 캡처한 것. */
    private const val SEED_DESIGN_IMAGE = "drawable/seed_group_design.png"

    fun seed(room: ChatRoom.Item, myId: String): List<Chat.Item> {
        val members = UserListCodec.decode(room.totalUserList)
        if (members.isEmpty()) return emptyList()

        val myName = TestAccounts.find(myId)?.userName ?: myId
        val others = members.filterNot { it.first.equals(myId, ignoreCase = true) }
        val lines = scriptFor(room, others.map { it.second }, myName)
        if (lines.isEmpty()) return emptyList()

        val lastMillis = parseMillis(room.lastChatDate) ?: return emptyList()

        val speakers = lines.map { line ->
            if (line.mine || others.isEmpty()) myId to myName else others[line.speakerIndex % others.size]
        }
        val everyone = listOf(myId to myName) + others

        /** 마지막 줄이 방의 lastChatDate 와 같은 시각이 되도록 역순으로 시간을 매긴다. 답장은 앞의 대화를 가리키므로 앞에서부터 만든다. */
        val chats = mutableListOf<Chat.Item>()
        lines.forEachIndexed { index, line ->
            val fromEnd = lines.lastIndex - index
            val millis = lastMillis - fromEnd * GAP_MINUTES * 60_000L
            val speaker = speakers[index]
            val chatId = chatIdAt(millis, speaker.first, index)
            val base = Chat.Item(
                chatID = chatId,
                chatType = Chat.Type.TEXT,
                chatContent = line.text,
                chatStatus = Chat.Status.COMPLETE,
                date = formatChatDate(millis),
                unReadCount = "0",
                user = User(id = speaker.first, name = speaker.second),
                isMe = speaker.first == myId,
                empathy = empathyOf(chatId, line.empathy, everyone.filterNot { it == speaker }),
            )
            chats += when (val body = line.body) {
                Body.Text -> base
                is Body.Picture -> base.copy(
                    chatType = Chat.Type.IMAGE,
                    chatContent = "",
                    imagePath = resourceUri(body.resourcePath),
                    imageSize = body.size,
                )
                is Body.Attachment -> base.copy(
                    chatType = Chat.Type.FILE,
                    chatContent = body.sizeLabel,
                    originalFileName = body.fileName,
                )
                is Body.Sticker -> base.copy(chatType = Chat.Type.EMOTICON, emoticon = Emoticon(id = body.emoticonId))
                is Body.Reply -> chats[body.toIndex].let { origin ->
                    base.copy(
                        chatType = Chat.Type.REPLY,
                        replyChat = ReplyChat(
                            chatID = origin.chatID,
                            chatType = origin.chatType,
                            chatContent = origin.chatContent,
                            user = origin.user,
                            emoticon = origin.emoticon,
                            imagePath = origin.imagePath,
                        ),
                    )
                }
            }
        }
        return chats
    }

    /**
     * 앱에 든 리소스의 주소. JVM 단위 테스트에는 Android 컨텍스트가 없어 주소를 못 구하므로 경로를 그대로 둔다 —
     * 테스트는 대화 수·종류만 보고 사진을 그리지 않는다.
     */
    private fun resourceUri(path: String): String = runCatching { Res.getUri(path) }.getOrDefault(path)

    /** 공감 아이콘 번호(0~5)별로 누른 사람 수만큼 [reactors] 앞에서부터 채운다. */
    private fun empathyOf(chatId: String, counts: Map<Int, Int>, reactors: List<Pair<String, String>>): EmpathyChat {
        if (counts.isEmpty()) return EmpathyChat()
        var cursor = 0
        val byIcon = counts.mapValues { (_, count) ->
            List(count) { reactors[(cursor++) % reactors.size] }.map { User(id = it.first, name = it.second) }
        }
        return EmpathyChat(
            chatID = chatId,
            empathy0 = byIcon[0].orEmpty(),
            empathy1 = byIcon[1].orEmpty(),
            empathy2 = byIcon[2].orEmpty(),
            empathy3 = byIcon[3].orEmpty(),
            empathy4 = byIcon[4].orEmpty(),
            empathy5 = byIcon[5].orEmpty(),
        )
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
        /** 상대가 모두 퇴장한 방은 [seed] 가 화자를 전부 나로 배정하므로 안읽음이 없다. */
        if (others.isEmpty()) return emptyList()
        val lines = scriptFor(room, others.map { it.second }, myName)
        return lines.takeLastWhile { !it.mine }
    }

    /**
     * 대본 한 줄. [body] 로 대화 종류를 고르고, [empathy] 는 공감 아이콘 번호 → 누른 사람 수다.
     * 문자 대화가 아니면 [text] 는 비워 둔다(답장은 답장 본문).
     */
    private data class Line(
        val text: String,
        val mine: Boolean,
        val speakerIndex: Int = 0,
        val body: Body = Body.Text,
        val empathy: Map<Int, Int> = emptyMap(),
    )

    /** 문자 말고 시연할 대화 종류. 사진은 앱에 든 리소스를 쓰고, 파일은 이름과 크기만 보인다. */
    private sealed interface Body {
        data object Text : Body
        data class Picture(val resourcePath: String, val size: String) : Body
        data class Attachment(val fileName: String, val sizeLabel: String) : Body
        data class Sticker(val emoticonId: String) : Body
        /** [toIndex] 번째 줄에 대한 답장. */
        data class Reply(val toIndex: Int) : Body
    }

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

            /** 스크린샷 시연용 — 사진·파일·이모티콘·답장·멘션·공감이 한 화면에 들어오게 짰다. */
            room.id == "room_group_dev" -> listOf(
                Line("오늘 스프린트 리뷰 3시에 시작합니다", mine = true),
                Line("", mine = false, speakerIndex = 0, body = Body.Sticker("(손말_안녕하세오)")),
                Line("", mine = false, speakerIndex = 1, body = Body.Picture(SEED_DESIGN_IMAGE, "414:230"), empathy = mapOf(0 to 2, 1 to 1)),
                Line("@$myName 내 그룹 화면 시안입니다. 확인 부탁드려요", mine = false, speakerIndex = 1),
                Line("", mine = false, speakerIndex = 2, body = Body.Attachment("QA_체크리스트_v2.xlsx", "248KB")),
                Line("", mine = false, speakerIndex = 0, body = Body.Sticker("(손말_오케이)")),
                Line("좋네요. 검색창 위 여백만 조금 줄여 주세요", mine = true, body = Body.Reply(toIndex = 3), empathy = mapOf(1 to 2, 2 to 1)),
            )

            /** 스크롤·페이징 확인용 긴 방 — 총 35줄, 마지막 10줄이 상대 대화라 안읽음 10. */
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

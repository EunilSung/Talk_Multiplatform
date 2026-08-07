package com.eunilsung.talk.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 채팅 도메인 공용 ID / 날짜 유틸. chatId·chatRoomId 포맷: `yyyyMMddHHmmssSSS.{userId}`. */
object ChatIdUtils {

    /** 현재 시각 기준 chatId 생성. */
    fun generateChatId(myId: String): String = generateChatId(nowAsLocalDateTime(), myId)

    /** 지정 시각 기준 chatId 생성. */
    fun generateChatId(now: LocalDateTime, myId: String): String = buildString {
        append(now.year.toString().padStart(4, '0'))
        append(now.monthNumber.toString().padStart(2, '0'))
        append(now.dayOfMonth.toString().padStart(2, '0'))
        append(now.hour.toString().padStart(2, '0'))
        append(now.minute.toString().padStart(2, '0'))
        append(now.second.toString().padStart(2, '0'))
        append((now.nanosecond / 1_000_000).toString().padStart(3, '0'))
        append('.')
        append(myId)
    }

    /** chat 의 `date` 컬럼 포맷 — `yyyy-MM-dd HH:mm:ss:SSS`. */
    fun formatChatDate(now: LocalDateTime): String {
        val y = now.year.toString().padStart(4, '0')
        val mo = now.monthNumber.toString().padStart(2, '0')
        val d = now.dayOfMonth.toString().padStart(2, '0')
        val h = now.hour.toString().padStart(2, '0')
        val mi = now.minute.toString().padStart(2, '0')
        val s = now.second.toString().padStart(2, '0')
        val ms = (now.nanosecond / 1_000_000).toString().padStart(3, '0')
        return "$y-$mo-$d $h:$mi:$s:$ms"
    }

    /** 현재 시각 → 시스템 기본 TZ 기준 [LocalDateTime]. */
    fun nowAsLocalDateTime(): LocalDateTime {
        val millis = kotlin.time.Clock.System.now().toEpochMilliseconds()
        return kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }
}

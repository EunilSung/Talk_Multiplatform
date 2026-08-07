package com.eunilsung.talk.util

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object DateUtils {
    fun convertChatRoomDate(chatroomDate: String?): ChatDateDisplay {
        if (chatroomDate.isNullOrBlank()) return ChatDateDisplay.Raw("")
        
        val parts = chatroomDate.split(" ")
        if (parts.size != 2) return ChatDateDisplay.Raw(chatroomDate)
        
        val datePart = parts[0]
        val timePart = parts[1]
        
        val dateSubParts = datePart.split("-")
        if (dateSubParts.size != 3) return ChatDateDisplay.Raw(chatroomDate)
        
        val year = dateSubParts[0].toIntOrNull() ?: return ChatDateDisplay.Raw(chatroomDate)
        val month = dateSubParts[1].toIntOrNull() ?: return ChatDateDisplay.Raw(chatroomDate)
        val day = dateSubParts[2].toIntOrNull() ?: return ChatDateDisplay.Raw(chatroomDate)
        
        val timeSubParts = timePart.split(":")
        if (timeSubParts.size < 2) return ChatDateDisplay.Raw(chatroomDate)
        
        val hour = timeSubParts[0].toIntOrNull() ?: return ChatDateDisplay.Raw(chatroomDate)
        val minute = timeSubParts[1].toIntOrNull() ?: return ChatDateDisplay.Raw(chatroomDate)

        val currentMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val now = kotlinx.datetime.Instant.fromEpochMilliseconds(currentMillis).toLocalDateTime(TimeZone.currentSystemDefault())

        val roomDate = kotlinx.datetime.LocalDate(year, month, day)
        val today = now.date

        val daysDiff = (today.toEpochDays() - roomDate.toEpochDays()).toLong()

        return when {
            daysDiff == 0L -> {
                ChatDateDisplay.Time(hour, minute)
            }
            daysDiff == 1L -> ChatDateDisplay.Yesterday
            now.year == year -> ChatDateDisplay.MonthDay(month, day)
            else -> ChatDateDisplay.Raw(datePart)
        }
    }

    /** 대화방 날짜 구분선 표시용 — "yyyy년 M월 d일" 로 변환 (형식이 깨졌으면 원본 반환). */
    fun convertChatDateHeader(chatDate: String?): ChatDateDisplay {
        if (chatDate.isNullOrBlank()) return ChatDateDisplay.Raw("")

        val parts = chatDate.split(" ")
        if (parts.isEmpty()) return ChatDateDisplay.Raw(chatDate)
        val datePart = parts[0]

        val dateSubParts = datePart.split("-")
        if (dateSubParts.size != 3) return ChatDateDisplay.Raw(chatDate)

        val year = dateSubParts[0].toIntOrNull() ?: return ChatDateDisplay.Raw(chatDate)
        val month = dateSubParts[1].toIntOrNull() ?: return ChatDateDisplay.Raw(chatDate)
        val day = dateSubParts[2].toIntOrNull() ?: return ChatDateDisplay.Raw(chatDate)

        return ChatDateDisplay.YearMonthDay(year, month, day)
    }

    /** 대화 시간 표시용 — "오전/오후 h:mm" 형태로 변환 (형식이 깨졌으면 원본 반환). */
    fun convertChatDate(chatDate: String?): ChatDateDisplay {
        if (chatDate.isNullOrBlank()) return ChatDateDisplay.Raw("")

        val parts = chatDate.split(" ")
        if (parts.size != 2) return ChatDateDisplay.Raw(chatDate)
        val timePart = parts[1]

        val timeSubParts = timePart.split(":")
        if (timeSubParts.size < 2) return ChatDateDisplay.Raw(chatDate)

        val hour = timeSubParts[0].toIntOrNull() ?: return ChatDateDisplay.Raw(chatDate)
        val minute = timeSubParts[1].toIntOrNull() ?: return ChatDateDisplay.Raw(chatDate)

        return ChatDateDisplay.Time(hour, minute)
    }
}

/**
 * 날짜 표기 결과 — 문자열이 아니라 "무엇을 보여줄지" 만 담는다.
 *
 * util 이 직접 문자열을 만들면 한국어가 박혀 영어 로케일에서 화면이 섞인다.
 * 실제 문구는 표시 계층([com.eunilsung.talk.ui.util.rememberChatTime] 등)이 리소스로 만든다.
 */
sealed interface ChatDateDisplay {
    data class Time(val hour: Int, val minute: Int) : ChatDateDisplay
    data object Yesterday : ChatDateDisplay
    data class MonthDay(val month: Int, val day: Int) : ChatDateDisplay
    data class YearMonthDay(val year: Int, val month: Int, val day: Int) : ChatDateDisplay

    /** 파싱 실패 — 원본을 그대로 보여준다. */
    data class Raw(val text: String) : ChatDateDisplay
}

package com.eunilsung.talk.ui.util

import androidx.compose.runtime.Composable
import com.eunilsung.talk.util.ChatDateDisplay
import com.eunilsung.talk.util.DateUtils
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.date_ampm_am
import multiplatformtalk.composeapp.generated.resources.date_ampm_pm
import multiplatformtalk.composeapp.generated.resources.date_month_day
import multiplatformtalk.composeapp.generated.resources.date_time
import multiplatformtalk.composeapp.generated.resources.date_year_month_day
import multiplatformtalk.composeapp.generated.resources.date_yesterday
import org.jetbrains.compose.resources.stringResource

/** [ChatDateDisplay] 를 현재 로케일 문자열로 만든다. */
@Composable
fun ChatDateDisplay.localized(): String = when (this) {
    is ChatDateDisplay.Time -> stringResource(
        Res.string.date_time,
        stringResource(if (hour < 12) Res.string.date_ampm_am else Res.string.date_ampm_pm),
        if (hour % 12 == 0) 12 else hour % 12,
        minute.toString().padStart(2, '0'),
    )
    ChatDateDisplay.Yesterday -> stringResource(Res.string.date_yesterday)
    is ChatDateDisplay.MonthDay -> stringResource(Res.string.date_month_day, month, day)
    is ChatDateDisplay.YearMonthDay ->
        stringResource(Res.string.date_year_month_day, year, month, day)
    is ChatDateDisplay.Raw -> text
}

/** 대화 말풍선 옆 시각. */
@Composable
fun chatTimeText(date: String?): String = DateUtils.convertChatDate(date).localized()

/**
 * 콜백처럼 `@Composable` 이 아닌 곳에서 쓰는 시각 포맷.
 * 라벨을 미리 받아 두므로 호출부가 컴포지션 밖이어도 로케일이 유지된다.
 */
fun formatChatTime(date: String?, amLabel: String, pmLabel: String): String =
    when (val d = DateUtils.convertChatDate(date)) {
        is ChatDateDisplay.Time ->
            "${if (d.hour < 12) amLabel else pmLabel} " +
                "${if (d.hour % 12 == 0) 12 else d.hour % 12}:${d.minute.toString().padStart(2, '0')}"
        is ChatDateDisplay.Raw -> d.text
        else -> ""
    }

/** 목록 위 날짜 구분선. */
@Composable
fun chatDateHeaderText(date: String?): String = DateUtils.convertChatDateHeader(date).localized()

/** 대화방 목록의 마지막 대화 시각. */
@Composable
fun chatRoomDateText(date: String?): String = DateUtils.convertChatRoomDate(date).localized()

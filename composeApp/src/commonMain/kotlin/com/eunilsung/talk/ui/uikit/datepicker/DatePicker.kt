package com.eunilsung.talk.ui.uikit.datepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_left_icon
import multiplatformtalk.composeapp.generated.resources.ok
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.date_no_selectable
import multiplatformtalk.composeapp.generated.resources.date_year_month
import multiplatformtalk.composeapp.generated.resources.weekday_sun
import multiplatformtalk.composeapp.generated.resources.weekday_mon
import multiplatformtalk.composeapp.generated.resources.weekday_tue
import multiplatformtalk.composeapp.generated.resources.weekday_wed
import multiplatformtalk.composeapp.generated.resources.weekday_thu
import multiplatformtalk.composeapp.generated.resources.weekday_fri
import multiplatformtalk.composeapp.generated.resources.weekday_sat
import org.jetbrains.compose.resources.stringResource
import multiplatformtalk.composeapp.generated.resources.arrow_right_icon
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePicker(
    availableDates: List<String>,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val allowedDates: List<LocalDate> = remember(availableDates) {
        availableDates
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .distinct()
            .sorted()
    }

    val availableMonths: List<LocalDate> = remember(allowedDates) {
        allowedDates
            .map { LocalDate(it.year, it.monthNumber, 1) }
            .distinct()
            .sorted()
    }

    val pagerState = rememberPagerState(
        initialPage = (availableMonths.size - 1).coerceAtLeast(0),
        pageCount = { availableMonths.size }
    )
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val scope = rememberCoroutineScope()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    selectedDate?.let { onDateSelected(it.toString()) }
                    onDismiss()
                },
                enabled = selectedDate != null
            ) {
                Text(
                    text = stringResource(Res.string.ok),
                    color = AppColors.Main,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.cancel),
                    color = AppColors.Main,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        colors = DatePickerDefaults.colors(containerColor = Color.White)
    ) {
        if (availableMonths.isEmpty()) {
            Text(
                text = stringResource(Res.string.date_no_selectable),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                textAlign = TextAlign.Center,
                color = Color.Gray
            )
            return@DatePickerDialog
        }

        Column(
            modifier = Modifier
                .background(Color.White)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val currentMonth = availableMonths.getOrNull(pagerState.currentPage)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = pagerState.currentPage > 0,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.arrow_left_icon),
                        contentDescription = "과거 달로 이동"
                    )
                }

                Text(
                    text = currentMonth?.let { stringResource(Res.string.date_year_month, it.year, it.monthNumber) } ?: "",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                IconButton(
                    enabled = pagerState.currentPage < availableMonths.size - 1,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.arrow_right_icon),
                        contentDescription = "최신 달로 이동"
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    stringResource(Res.string.weekday_sun), stringResource(Res.string.weekday_mon),
                    stringResource(Res.string.weekday_tue), stringResource(Res.string.weekday_wed),
                    stringResource(Res.string.weekday_thu), stringResource(Res.string.weekday_fri),
                    stringResource(Res.string.weekday_sat),
                ).forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.height(280.dp),
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                CalendarGrid(
                    month = availableMonths[pageIndex],
                    allowedDates = allowedDates,
                    selectedDate = selectedDate,
                    onDateClick = { selectedDate = it }
                )
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    month: LocalDate,
    allowedDates: List<LocalDate>,
    selectedDate: LocalDate?,
    onDateClick: (LocalDate) -> Unit,
) {
    val cells = remember(month) { generateMonthCells(month) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        items(cells.size) { i ->
            val date = cells[i]
            if (date == null) {
                Box(modifier = Modifier.aspectRatio(1f))
            } else {
                val isAllowed = date in allowedDates
                val isSelected = date == selectedDate

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(2.dp)
                        .background(
                            color = if (isSelected) AppColors.Main else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable(enabled = isAllowed) { onDateClick(date) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        color = when {
                            isSelected -> Color.White
                            isAllowed -> Color.Black
                            else -> Color.LightGray
                        },
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun generateMonthCells(monthFirstDay: LocalDate): List<LocalDate?> {
    val firstDayOfWeek = (monthFirstDay.dayOfWeek.ordinal + 1) % 7

    val nextMonthFirstDay = monthFirstDay.plus(1, DateTimeUnit.MONTH)
    val daysInMonth: Int = nextMonthFirstDay.plus(-1, DateTimeUnit.DAY).dayOfMonth

    val list = mutableListOf<LocalDate?>()
    repeat(firstDayOfWeek) { list.add(null) }
    for (day in 1..daysInMonth) {
        list.add(LocalDate(monthFirstDay.year, monthFirstDay.monthNumber, day))
    }
    return list
}

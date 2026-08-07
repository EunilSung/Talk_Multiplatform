package com.eunilsung.talk.ui.setting.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.apply
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.noti_time_end
import multiplatformtalk.composeapp.generated.resources.noti_time_overnight
import multiplatformtalk.composeapp.generated.resources.noti_time_start
import multiplatformtalk.composeapp.generated.resources.noti_time_title
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.clickable
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import org.jetbrains.compose.resources.stringResource

/**
 * 알림 시간 설정 시트 — 시작/종료 시간(24h) 을 다이얼로 선택.
 * @param initialValue 기존 값 "시작hhmm종료hhmm"(예 "09001800"). 비면 09:00~18:00 기본.
 * @param onConfirm 확정 시 "hhmmhhmm" 값 전달.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotiTimePickerSheetContent(
    initialValue: String,
    onCancel: () -> Unit,
    onConfirm: (value: String) -> Unit,
) {
    val start = initialValue.take(4)
    val end = initialValue.drop(4).take(4)

    fun hour(s: String, default: Int) = s.take(2).toIntOrNull()?.takeIf { it in 0..23 } ?: default
    fun minute(s: String, default: Int) = s.drop(2).take(2).toIntOrNull()?.takeIf { it in 0..59 } ?: default

    val startState = rememberTimePickerState(hour(start, 9), minute(start, 0), is24Hour = true)
    val endState = rememberTimePickerState(hour(end, 18), minute(end, 0), is24Hour = true)

    var editingEnd by remember { mutableStateOf(false) }
    val current = if (editingEnd) endState else startState

    val toast = LocalToastManager.current
    val txtOvernight = stringResource(Res.string.noti_time_overnight)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.noti_time_title),
            color = AppColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeChip(
                label = stringResource(Res.string.noti_time_start),
                time = display(startState),
                selected = !editingEnd,
                onClick = { editingEnd = false },
                modifier = Modifier.weight(1f),
            )
            TimeChip(
                label = stringResource(Res.string.noti_time_end),
                time = display(endState),
                selected = editingEnd,
                onClick = { editingEnd = true },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))
        TimePicker(state = current)

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.cancel), color = AppColors.TextSub)
            }
            Button(
                onClick = {
                    val startMin = startState.hour * 60 + startState.minute
                    val endMin = endState.hour * 60 + endState.minute
                    if (startMin > endMin) {
                        toast.show(txtOvernight)
                    }
                    onConfirm(value(startState) + value(endState))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.apply))
            }
        }
    }
}

/** 표시용 "HH:MM". */
@OptIn(ExperimentalMaterial3Api::class)
private fun display(state: TimePickerState): String =
    state.hour.toString().padStart(2, '0') + ":" + state.minute.toString().padStart(2, '0')

/** 전송용 "HHMM". */
@OptIn(ExperimentalMaterial3Api::class)
private fun value(state: TimePickerState): String =
    state.hour.toString().padStart(2, '0') + state.minute.toString().padStart(2, '0')

@Composable
private fun TimeChip(
    label: String,
    time: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AppColors.SkyBg else AppColors.Gray50Bg)
            .clickable(cornerRadius = 10.dp, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.Main else AppColors.TextSub,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = time,
            color = AppColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

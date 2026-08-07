package com.eunilsung.talk.ui.chatroom.vote.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.domain.model.VoteOption
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.chatroom.vote.VoteActions
import com.eunilsung.talk.ui.chatroom.vote.VoteUiState
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.vote_multi_info
import multiplatformtalk.composeapp.generated.resources.vote_single_info
import multiplatformtalk.composeapp.generated.resources.vote_see_result
import multiplatformtalk.composeapp.generated.resources.vote_do
import org.jetbrains.compose.resources.stringResource

@Composable
fun VoteParticipateContent(
    state: VoteUiState.Participate,
    onAction: (VoteActions) -> Unit,
) {
    val detail = state.detail

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = "Q. ${detail.title}",
                color = AppColors.Text,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = if (detail.multiSelect) stringResource(Res.string.vote_multi_info) else stringResource(Res.string.vote_single_info),
                color = AppColors.TextSub,
                fontSize = 12.sp,
            )

            Spacer(modifier = Modifier.size(16.dp))
            detail.options.forEach { option ->
                OptionRow(
                    option = option,
                    selected = option.idx in state.selected,
                    multiSelect = detail.multiSelect,
                    onClick = { onAction(VoteActions.OnToggleOption(option.idx)) },
                )
                Spacer(modifier = Modifier.size(10.dp))
            }

            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = stringResource(Res.string.vote_see_result),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onAction(VoteActions.OnViewResult) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = AppColors.TextSub,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        val canSubmit = state.selected.isNotEmpty() && !state.isSubmitting
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (canSubmit) AppColors.Main else AppColors.Line)
                .clickable(enabled = canSubmit) { onAction(VoteActions.OnSubmitVote) }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.vote_do),
                color = AppColors.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun OptionRow(
    option: VoteOption,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AppColors.Main else AppColors.Line,
                shape = RoundedCornerShape(10.dp),
            )
            .background(if (selected) AppColors.Main.copy(alpha = 0.06f) else AppColors.BgSub)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectIndicator(selected = selected, multiSelect = multiSelect)
        Text(
            text = option.content,
            color = AppColors.Text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SelectIndicator(selected: Boolean, multiSelect: Boolean) {
    val shape = if (multiSelect) RoundedCornerShape(5.dp) else RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(shape)
            .background(if (selected) AppColors.Main else AppColors.Transparent)
            .border(
                width = if (selected) 0.dp else 1.5.dp,
                color = AppColors.Border,
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.White),
            )
        }
    }
}

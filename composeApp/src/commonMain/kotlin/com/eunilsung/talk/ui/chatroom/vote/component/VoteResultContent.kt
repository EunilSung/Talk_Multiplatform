package com.eunilsung.talk.ui.chatroom.vote.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.eunilsung.talk.domain.model.VoteResultItem
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.chatroom.vote.VoteActions
import com.eunilsung.talk.ui.chatroom.vote.VoteUiState
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.vote_total
import multiplatformtalk.composeapp.generated.resources.vote_ended
import multiplatformtalk.composeapp.generated.resources.vote_redo
import multiplatformtalk.composeapp.generated.resources.vote_close
import multiplatformtalk.composeapp.generated.resources.vote_rank_first
import org.jetbrains.compose.resources.stringResource

@Composable
fun VoteResultContent(
    state: VoteUiState.Result,
    onAction: (VoteActions) -> Unit,
) {
    val result = state.result
    val maxVote = result.items.maxOfOrNull { it.nVote } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Q. ${result.title}",
            color = AppColors.Text,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = stringResource(Res.string.vote_total, result.totalVotes) +
                if (result.isClosed) " · " + stringResource(Res.string.vote_ended) else "",
            color = AppColors.TextSub,
            fontSize = 12.sp,
        )

        Spacer(modifier = Modifier.size(20.dp))
        result.items.forEach { item ->
            ResultRow(
                item = item,
                total = result.totalVotes,
                isWinner = item.nVote > 0 && item.nVote == maxVote,
            )
            Spacer(modifier = Modifier.size(14.dp))
        }

        if (result.canReVote) {
            Spacer(modifier = Modifier.size(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.Main, RoundedCornerShape(10.dp))
                    .clickable { onAction(VoteActions.OnReVote) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.vote_redo),
                    color = AppColors.Main,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (result.canClose) {
            Spacer(modifier = Modifier.size(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Main)
                    .clickable { onAction(VoteActions.OnCloseVote) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.vote_close),
                    color = AppColors.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ResultRow(
    item: VoteResultItem,
    total: Int,
    isWinner: Boolean,
) {
    val fraction = if (total > 0) item.nVote.toFloat() / total else 0f
    val percent = (fraction * 100).toInt()
    val barColor = if (isWinner) AppColors.Main else AppColors.TextSub.copy(alpha = 0.35f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isWinner) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppColors.Main)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(text = stringResource(Res.string.vote_rank_first), color = AppColors.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.size(6.dp))
            }
            Text(
                text = item.content,
                modifier = Modifier.weight(1f),
                color = AppColors.Text,
                fontSize = 14.sp,
                fontWeight = if (isWinner) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = "$percent% (${item.nVote})",
                color = if (isWinner) AppColors.Main else AppColors.TextSub,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(AppColors.Line),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(barColor),
                )
            }
        }
    }
}

package com.eunilsung.talk.ui.chatroom.vote.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.domain.model.VoteSummary
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.chatroom.vote.VoteActions
import com.eunilsung.talk.ui.chatroom.vote.VoteUiState
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.vote_empty
import multiplatformtalk.composeapp.generated.resources.vote_ongoing
import multiplatformtalk.composeapp.generated.resources.vote_closed_section
import multiplatformtalk.composeapp.generated.resources.vote_none
import multiplatformtalk.composeapp.generated.resources.vote_participants
import multiplatformtalk.composeapp.generated.resources.vote_ended
import multiplatformtalk.composeapp.generated.resources.vote_status_ongoing
import multiplatformtalk.composeapp.generated.resources.vote_status_closed
import org.jetbrains.compose.resources.stringResource

@Composable
fun VoteListContent(
    state: VoteUiState.List,
    onAction: (VoteActions) -> Unit,
) {
    if (state.votes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(Res.string.vote_empty),
                color = AppColors.TextSub,
                fontSize = 14.sp,
            )
        }
        return
    }

    val ongoing = state.votes.filterNot { it.isClosed }
    val closed = state.votes.filter { it.isClosed }
    val ongoingTitle = stringResource(Res.string.vote_ongoing)
    val closedTitle = stringResource(Res.string.vote_closed_section)
    val noneLabel = stringResource(Res.string.vote_none)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        voteSection(ongoingTitle, ongoing, noneLabel, onAction)
        item { Spacer(modifier = Modifier.size(8.dp)) }
        voteSection(closedTitle, closed, noneLabel, onAction)
    }
}

private fun LazyListScope.voteSection(
    title: String,
    votes: List<VoteSummary>,
    noneLabel: String,
    onAction: (VoteActions) -> Unit,
) {
    item(key = "header_$title") {
        Text(
            text = "$title ${votes.size}",
            color = AppColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
    if (votes.isEmpty()) {
        item(key = "empty_$title") {
            Text(
                text = noneLabel,
                color = AppColors.TextSub,
                fontSize = 12.sp,
            )
        }
    } else {
        items(items = votes, key = { it.id }) { summary ->
            VoteListCard(summary = summary, onClick = { onAction(VoteActions.OnVoteClick(summary)) })
        }
    }
}

@Composable
private fun VoteListCard(
    summary: VoteSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Line, RoundedCornerShape(12.dp))
            .background(AppColors.BgSub)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary.title,
                modifier = Modifier.weight(1f),
                color = AppColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.size(8.dp))
            StatusChip(summary = summary)
        }
        Spacer(modifier = Modifier.size(8.dp))
        val info = stringResource(Res.string.vote_participants, summary.participantCount) +
            if (summary.isClosed && summary.endTime.isNotBlank())
                " · " + summary.endTime + " " + stringResource(Res.string.vote_ended) else ""
        Text(
            text = info,
            color = AppColors.TextSub,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun StatusChip(summary: VoteSummary) {
    val (label, color) = when {
        summary.isClosed -> stringResource(Res.string.vote_status_closed) to AppColors.TextSub
        else -> stringResource(Res.string.vote_status_ongoing) to AppColors.Main
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

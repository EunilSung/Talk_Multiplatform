package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.Vote
import com.eunilsung.talk.domain.model.VoteComplete
import com.eunilsung.talk.domain.model.VoteResultItem
import com.eunilsung.talk.ui.theme.AppColors
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.vote_closed_msg
import multiplatformtalk.composeapp.generated.resources.vote_do_or_view
import multiplatformtalk.composeapp.generated.resources.vote_rank_first
import multiplatformtalk.composeapp.generated.resources.vote_view_result
import org.jetbrains.compose.resources.stringResource

@Composable
fun VoteItem(
    itemProps: ChatItemProps
) {
    val vote = itemProps.chat.vote

    ChatBubbleRow(
        itemProps = itemProps,
        modifier = Modifier
    ) {
        ChatCardBubble(itemProps) {
            Column(modifier = Modifier.padding(CardPadding)) {
                VoteTitle(itemProps.chat.title)
                Spacer(modifier = Modifier.size(14.dp))
                vote?.items?.take(4)?.forEachIndexed { index, item ->
                    if (index > 0) Spacer(modifier = Modifier.size(14.dp))
                    Text(
                        text = item,
                        modifier = Modifier.padding(horizontal = 5.dp),
                        color = AppColors.TextSub,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ChatCardActionRow(
                text = stringResource(Res.string.vote_do_or_view),
                onClick = itemProps.onVoteClick,
                onLongClick = itemProps.onLongClick,
            )
        }
    }
}

@Composable
fun VoteCompleteItem(
    itemProps: ChatItemProps
) {
    val voteComplete = itemProps.chat.voteComplete

    ChatBubbleRow(
        itemProps = itemProps,
        modifier = Modifier
    ) {
        ChatCardBubble(itemProps) {
            ChatCardHeader(stringResource(Res.string.vote_closed_msg))
            Column(modifier = Modifier.padding(CardPadding)) {
                VoteTitle(itemProps.chat.title)

                val winner = voteComplete?.items?.maxByOrNull { it.nVote }
                if (winner != null) {
                    Spacer(modifier = Modifier.size(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 5.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.PrimaryBg)
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(AppColors.Main)
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.vote_rank_first),
                                color = AppColors.White,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = winner.content,
                            color = AppColors.Main,
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            ChatCardActionRow(
                text = stringResource(Res.string.vote_view_result),
                onClick = itemProps.onVoteClick,
                onLongClick = itemProps.onLongClick,
            )
        }
    }
}

@Composable
private fun VoteTitle(title: String) {
    Text(
        text = "Q. $title",
        modifier = Modifier.padding(horizontal = 5.dp),
        color = AppColors.Text,
        fontSize = 13.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        overflow = TextOverflow.Ellipsis,
    )
}

@Preview(showBackground = true)
@Composable
fun VoteItemPreview() {
    MaterialTheme {
        VoteItem(
            ChatItemProps(
                chat = Chat.Item(
                    title = "점심 뭐 먹을까요?",
                    vote = Vote(items = listOf("김치찌개", "돈까스", "샐러드")),
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun VoteCompleteItemPreview() {
    MaterialTheme {
        VoteCompleteItem(
            ChatItemProps(
                chat = Chat.Item(
                    title = "점심 뭐 먹을까요?",
                    voteComplete = VoteComplete(
                        items = listOf(
                            VoteResultItem(content = "김치찌개", nVote = 5),
                            VoteResultItem(content = "돈까스", nVote = 2),
                        )
                    ),
                )
            )
        )
    }
}

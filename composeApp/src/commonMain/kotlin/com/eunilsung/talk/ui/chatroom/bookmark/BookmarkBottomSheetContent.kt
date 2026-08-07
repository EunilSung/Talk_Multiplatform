package com.eunilsung.talk.ui.chatroom.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.domain.model.Bookmark
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.line.LineDivider
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.bookmark_title
import multiplatformtalk.composeapp.generated.resources.bookmark_empty
import multiplatformtalk.composeapp.generated.resources.bookmark_remove
import org.jetbrains.compose.resources.stringResource

@Composable
fun BookmarkBottomSheetContent(
    bookmarksFlow: StateFlow<List<Bookmark>>,
    onItemClick: (Bookmark) -> Unit = {},
    onRemove: (Bookmark) -> Unit = {},
) {
    val bookmarks by bookmarksFlow.collectAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.bookmark_title),
                color = AppColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.bookmark_empty),
                    color = AppColors.TextSub,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                items(bookmarks, key = { it.chatId }) { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onClick = { onItemClick(bookmark) },
                        onRemove = { onRemove(bookmark) },
                    )
                    LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmark.content,
                color = AppColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${bookmark.date}  ${bookmark.userName}",
                color = AppColors.TextSub,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.BgSub)
                .clickable(onClick = onRemove)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.bookmark_remove),
                color = AppColors.Text,
                fontSize = 13.sp,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BookmarkBottomSheetContentPreview() {
    MaterialTheme {
        BookmarkBottomSheetContent(
            bookmarksFlow = kotlinx.coroutines.flow.MutableStateFlow(
                listOf(
                    Bookmark(
                        chatId = "1",
                        chatRoomId = "1",
                        content = "북마크 아이템",
                        date = "2023-01-01 00:00:00",
                        userId = "1",
                        userName = "성은일",
                    )
                )
            )
        )
    }
}

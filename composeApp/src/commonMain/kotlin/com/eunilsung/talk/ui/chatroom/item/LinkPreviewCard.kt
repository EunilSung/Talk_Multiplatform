package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.StateFlow
import com.eunilsung.talk.data.local.ExternalUrlOpener
import com.eunilsung.talk.data.remote.linkpreview.LinkPreview
import com.eunilsung.talk.data.remote.linkpreview.LinkPreviewRepository
import com.eunilsung.talk.data.remote.linkpreview.LinkPreviewState
import com.eunilsung.talk.ui.theme.AppColors
import org.koin.compose.koinInject

@Composable
fun LinkPreviewCard(
    url: String,
    modifier: Modifier = Modifier,
) {
    val repository: LinkPreviewRepository = koinInject()
    val urlOpener: ExternalUrlOpener = koinInject()

    var stateFlow by remember(url) { mutableStateOf<StateFlow<LinkPreviewState>?>(null) }
    LaunchedEffect(url) {
        com.eunilsung.talk.util.Log.message("[LinkPreview] LinkPreviewCard launchedEffect url=$url")
        stateFlow = repository.getState(url)
    }
    val flow = stateFlow
    if (flow == null) {
        LinkPreviewSkeleton(modifier = modifier)
        return
    }
    val state by flow.collectAsState()
    when (val s = state) {
        is LinkPreviewState.Loading -> LinkPreviewSkeleton(modifier = modifier)
        is LinkPreviewState.Success -> LinkPreviewContent(
            data = s.data,
            onClick = { urlOpener.open(url) },
            modifier = modifier,
        )
        is LinkPreviewState.Failure -> Unit
    }
}

@Composable
private fun LinkPreviewContent(
    data: LinkPreview,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.BgSub)
            .clickable(onClick = onClick)
    ) {
        if (data.imageUrl.isNotBlank()) {
            AsyncImage(
                model = data.imageUrl,
                contentDescription = data.title.ifBlank { data.url },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .background(AppColors.Gray100BgAuto),
            )
        }

        Column(modifier = Modifier.padding(12.dp)) {
            if (data.title.isNotBlank()) {
                Text(
                    text = data.title,
                    color = AppColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            if (data.description.isNotBlank()) {
                Text(
                    text = data.description,
                    color = AppColors.TextSub,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = data.displayDomain,
                color = AppColors.PrimaryMain,
                fontSize = 12.sp,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LinkPreviewSkeleton(modifier: Modifier) {
    Box(
        modifier = modifier
            .widthIn(max = 200.dp)
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.BgSub),
    )
}

@Preview(showBackground = true)
@Composable
private fun LinkPreviewContentPreview() {
    MaterialTheme {
        LinkPreviewContent(
            data = LinkPreview(
                url = "https://www.example.com/page",
                title = "예시 페이지 타이틀",
                description = "링크 미리보기 설명 텍스트입니다.",
            ),
            onClick = {},
            modifier = Modifier,
        )
    }
}

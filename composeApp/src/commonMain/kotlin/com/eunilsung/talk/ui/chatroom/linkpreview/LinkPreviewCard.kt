package com.eunilsung.talk.ui.chatroom.linkpreview

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
import com.eunilsung.talk.data.local.ExternalUrlOpener
import com.eunilsung.talk.domain.model.LinkPreview
import com.eunilsung.talk.domain.model.LinkPreviewState
import com.eunilsung.talk.ui.theme.AppColors
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.link_preview_show
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.eunilsung.talk.util.Log

/**
 * 대화 링크의 미리보기 카드.
 *
 * [isMe] 는 내가 보낸 대화인지다. 받을지는 [LinkPreviewViewModel] 이 정한다 — 받은 대화는 '미리보기 보기'를 눌러야 받아 온다.
 * 카드를 누르면 브라우저로만 연다([ExternalUrlOpener.open]) — 주소가 무엇이든 내려받거나 설치하지 않는다.
 */
@Composable
fun LinkPreviewCard(
    url: String,
    isMe: Boolean,
    modifier: Modifier = Modifier,
) {
    val viewModel: LinkPreviewViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val urlOpener: ExternalUrlOpener = koinInject()

    LaunchedEffect(url, isMe) {
        Log.message("[LinkPreview] LinkPreviewCard launchedEffect url=$url isMe=$isMe")
        viewModel.onAction(LinkPreviewActions.OnShown(url, isMe))
    }
    val state = uiState.previews[url]
    when {
        url in uiState.waitingUrls -> LinkPreviewLoadButton(
            onClick = { viewModel.onAction(LinkPreviewActions.Load(url)) },
            modifier = modifier,
        )
        state is LinkPreviewState.Success -> LinkPreviewContent(
            data = state.data,
            onClick = { urlOpener.open(url) },
            modifier = modifier,
        )
        state is LinkPreviewState.Loading || (state == null && isMe) -> LinkPreviewSkeleton(modifier = modifier)
        else -> Unit
    }
}

/** 받은 대화의 링크 — 누르면 그때 미리보기를 받아 온다. */
@Composable
private fun LinkPreviewLoadButton(
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Text(
        text = stringResource(Res.string.link_preview_show),
        color = AppColors.PrimaryMain,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.BgSub)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
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

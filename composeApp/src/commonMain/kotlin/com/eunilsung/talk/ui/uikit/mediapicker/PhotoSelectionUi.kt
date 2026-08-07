package com.eunilsung.talk.ui.uikit.mediapicker

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import multiplatformtalk.composeapp.generated.resources.a11y_photo_cell
import multiplatformtalk.composeapp.generated.resources.a11y_video_cell
import multiplatformtalk.composeapp.generated.resources.a11y_selection_order
import multiplatformtalk.composeapp.generated.resources.a11y_not_selected
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import coil3.compose.AsyncImage
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.send
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.stringResource

@Composable
fun SelectablePhotoCell(
    photo: MultimediaRecentPhoto,
    selectionOrder: Int,
    onToggleSelect: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 10.dp,
    isShowToggle: Boolean = true
) {
    val selected = selectionOrder > 0
    // 셀 전체가 클릭 대상이라 스크린리더가 "버튼" 만 읽는다.
    // 무엇인지(사진/동영상) + 선택 순번을 함께 전달한다.
    val kindLabel = stringResource(
        if (photo.isVideo) Res.string.a11y_video_cell else Res.string.a11y_photo_cell
    )
    val stateLabel = if (selected) {
        stringResource(Res.string.a11y_selection_order, selectionOrder)
    } else {
        stringResource(Res.string.a11y_not_selected)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .semantics {
                contentDescription = kindLabel
                stateDescription = stateLabel
                role = Role.Checkbox
            }
            .clickable { onToggleSelect() }
            .background(AppColors.SearchBG)
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    color = AppColors.Main,
                    shape = RoundedCornerShape(cornerRadius)
                ) else Modifier
            )
    ) {
        val bytes = photo.thumbnailBytes
        val uri = photo.uri
        when {
            bytes != null -> {
                AsyncImage(
                    model = bytes,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            !uri.isNullOrBlank() -> {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> Unit
        }

        if (isShowToggle){
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Black.copy(alpha = 0.35f))
                )
            }

            SelectionBadge(
                order = selectionOrder,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AppColors.Black.copy(alpha = 0.55f))
                    .clickable { onDetailClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⛶",
                    color = AppColors.White,
                    fontSize = 13.sp,
                    lineHeight = 13.sp
                )
            }
        }

        if (photo.isVideo) {
            VideoDurationBadge(
                durationSec = photo.durationSec,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        }
    }
}

/** 동영상 셀 우하단 배지 — 재생 아이콘 + 길이(mm:ss). */
@Composable
private fun VideoDurationBadge(
    durationSec: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.Black.copy(alpha = 0.6f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "▶", color = AppColors.White, fontSize = 8.sp, lineHeight = 8.sp)
        Spacer(Modifier.width(3.dp))
        Text(
            text = formatMediaDuration(durationSec),
            color = AppColors.White,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 초 → "m:ss" (1시간 이상은 "h:mm:ss"). */
internal fun formatMediaDuration(totalSec: Long): String {
    if (totalSec <= 0) return "0:00"
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}

@Composable
fun SelectionBadge(
    order: Int,
    modifier: Modifier = Modifier,
) {
    val selected = order > 0
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                if (selected) AppColors.Main
                else AppColors.White.copy(alpha = 0.35f)
            )
            .border(
                width = 1.5.dp,
                color = AppColors.White,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text(
                text = order.toString(),
                color = AppColors.White,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PhotoActionBar(
    selectedCount: Int,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    leftContent: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(contentAlignment = Alignment.CenterStart) { leftContent() }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectedCount > 0) {
                Text(
                    text = selectedCount.toString(),
                    color = AppColors.PrimaryMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
            }
            val enabled = selectedCount > 0
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (enabled) AppColors.PrimaryMain else AppColors.Line)
                    .clickable(enabled = enabled) { onSend() }
                    .padding(horizontal = 14.dp, vertical = 3.dp)
            ) {
                Text(
                    text = stringResource(Res.string.send),
                    color = AppColors.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectablePhotoCellPreview() {
    MaterialTheme {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectablePhotoCell(
                photo = MultimediaRecentPhoto(id = "1"),
                selectionOrder = 0,
                onToggleSelect = {},
                onDetailClick = {},
                modifier = Modifier.size(120.dp).aspectRatio(1f),
            )
            SelectablePhotoCell(
                photo = MultimediaRecentPhoto(id = "2"),
                selectionOrder = 1,
                onToggleSelect = {},
                onDetailClick = {},
                modifier = Modifier.size(120.dp).aspectRatio(1f),
            )
        }
    }
}

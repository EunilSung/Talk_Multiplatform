package com.eunilsung.talk.ui.uikit.mediapicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_right_icon
import multiplatformtalk.composeapp.generated.resources.file
import multiplatformtalk.composeapp.generated.resources.multimedia_camera_icon
import multiplatformtalk.composeapp.generated.resources.multimedia_file_icon
import multiplatformtalk.composeapp.generated.resources.multimedia_photo_icon
import multiplatformtalk.composeapp.generated.resources.photo
import multiplatformtalk.composeapp.generated.resources.recent_photo
import multiplatformtalk.composeapp.generated.resources.view_all
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

data class MultimediaRecentPhoto(
    val id: String,
    val uri: String? = null,
    val thumbnailBytes: ByteArray? = null,
    val serverFileName: String = "",
    val originalFileName: String = "",
    val isVideo: Boolean = false,
    val durationSec: Long = 0,
    /** 사진 상세보기 제목의 보낸 사람 이름. 대화 사진일 때만 채운다. */
    val senderName: String = "",
    /** 사진 상세보기 제목의 보낸 시간 원본(대화 날짜 문자열). 대화 사진일 때만 채운다. */
    val sentDate: String = "",
)

/** 시트에 보여줄 최근 사진 개수 — 호출부에서 미리 잘라 넘길 때도 쓴다. */
internal const val MAX_RECENT_PHOTOS = 12

@Composable
fun MultimediaBottomSheetContent(
    recentPhotos: List<MultimediaRecentPhoto> = emptyList(),
    selectedIds: List<String> = emptyList(),
    onCamera: () -> Unit = {},
    onPhotoToggle: (MultimediaRecentPhoto) -> Unit = {},
    onPhotoDetailClick: (MultimediaRecentPhoto) -> Unit = {},
    onSend: () -> Unit = {},
    onViewAllPhotos: () -> Unit = {},
    onFile: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        PhotoActionBar(
            selectedCount = selectedIds.size,
            onSend = onSend,
            modifier = Modifier.padding(horizontal = 8.dp),
            leftContent = {
                Text(
                    text = stringResource(Res.string.recent_photo),
                    color = AppColors.TextSub,
                    fontSize = 14.sp
                )
            }
        )

        Spacer(modifier = Modifier.size(10.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "camera") { CameraThumb(onClick = onCamera) }

            items(
                items = recentPhotos.take(MAX_RECENT_PHOTOS),
                key = { it.id }
            ) { photo ->
                val order = selectedIds.indexOf(photo.id).let { if (it < 0) 0 else it + 1 }
                SelectablePhotoCell(
                    photo = photo,
                    selectionOrder = order,
                    onToggleSelect = { onPhotoToggle(photo) },
                    onDetailClick = { onPhotoDetailClick(photo) },
                    modifier = Modifier.size(100.dp),
                )
            }

            item(key = "view_all") { ViewAllThumb(onClick = onViewAllPhotos) }
        }

        Spacer(Modifier.height(18.dp))

        MenuRow(
            label = stringResource(Res.string.photo),
            icon = painterResource(Res.drawable.multimedia_photo_icon),
            onClick = onViewAllPhotos
        )

        MenuRow(
            label = stringResource(Res.string.file),
            icon = painterResource(Res.drawable.multimedia_file_icon),
            onClick = onFile
        )
    }
}

@Composable
private fun CameraThumb(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.CameraBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(Res.drawable.multimedia_camera_icon),
            contentDescription = "Camera",
            tint = AppColors.Line,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun ViewAllThumb(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.CameraBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(Res.drawable.arrow_right_icon),
                contentDescription = "전체보기",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(Res.string.view_all),
                color = AppColors.Text,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    icon: Painter,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = AppColors.Text,
            fontSize = 14.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MultimediaBottomSheetContentPreview() {
    MaterialTheme {
        MultimediaBottomSheetContent(
            recentPhotos = List(8) { MultimediaRecentPhoto(id = "p$it") },
            selectedIds = listOf("p0", "p2"),
        )
    }
}

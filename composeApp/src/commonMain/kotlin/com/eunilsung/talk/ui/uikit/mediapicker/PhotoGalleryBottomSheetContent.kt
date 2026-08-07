package com.eunilsung.talk.ui.uikit.mediapicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.all
import multiplatformtalk.composeapp.generated.resources.arrow_down_icon
import multiplatformtalk.composeapp.generated.resources.arrow_up_icon
import multiplatformtalk.composeapp.generated.resources.close_icon
import multiplatformtalk.composeapp.generated.resources.multimedia_camera_icon
import com.eunilsung.talk.data.local.PhotoAlbum
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PhotoGalleryBottomSheetContent(
    photos: List<MultimediaRecentPhoto>,
    selectedIds: List<String>,
    onClose: () -> Unit,
    onSend: () -> Unit,
    onCameraClick: () -> Unit,
    onPhotoToggle: (MultimediaRecentPhoto) -> Unit,
    onPhotoDetailClick: (MultimediaRecentPhoto) -> Unit,
    albums: List<PhotoAlbum> = emptyList(),
    selectedAlbumId: String? = null,
    onAlbumSelect: (String?) -> Unit = {},
) {
    var showAlbumPicker by remember { mutableStateOf(false) }
    val selectedAlbumName = remember(albums, selectedAlbumId) {
        albums.firstOrNull { it.id == selectedAlbumId }?.name
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PhotoActionBar(
            selectedCount = selectedIds.size,
            onSend = onSend,
            leftContent = {
                // 앨범이 하나도 없으면(권한 없음/조회 실패) 선택기를 숨겨 빈 버튼만 남지 않게 한다.
                if (albums.isNotEmpty()) {
                    AlbumSelector(
                        label = selectedAlbumName ?: stringResource(Res.string.all),
                        expanded = showAlbumPicker,
                        onClick = { showAlbumPicker = !showAlbumPicker },
                    )
                }
            }
        )

        if (showAlbumPicker) {
            AlbumList(
                albums = albums,
                selectedAlbumId = selectedAlbumId,
                onSelect = {
                    onAlbumSelect(it)
                    showAlbumPicker = false
                },
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "camera") {
                CameraGalleryCell(onClick = onCameraClick)
            }

            items(items = photos, key = { it.id }) { photo ->
                val order = selectedIds.indexOf(photo.id).let { if (it < 0) 0 else it + 1 }
                SelectablePhotoCell(
                    photo = photo,
                    selectionOrder = order,
                    onToggleSelect = { onPhotoToggle(photo) },
                    onDetailClick = { onPhotoDetailClick(photo) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    cornerRadius = 0.dp,
                )
            }
        }
    }
}

@Composable
private fun CameraGalleryCell(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
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

/** 액션바 좌측의 앨범 이름 + 펼침 화살표. */
@Composable
private fun AlbumSelector(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = AppColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        Spacer(Modifier.size(4.dp))
        Icon(
            painter = painterResource(
                if (expanded) Res.drawable.arrow_up_icon else Res.drawable.arrow_down_icon
            ),
            contentDescription = null,
            tint = AppColors.TextSub,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * 앨범 목록 — 사진 그리드 자리를 대체해 표시한다.
 *
 * 시트 높이가 고정이라 목록을 그리드 위에 겹치면 스크롤이 두 겹이 된다.
 * "목록을 고르면 다시 그리드" 흐름이라 자리를 바꿔 쓰는 편이 단순하다.
 */
@Composable
private fun AlbumList(
    albums: List<PhotoAlbum>,
    selectedAlbumId: String?,
    onSelect: (String?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp),
    ) {
        item(key = "__all__") {
            val first = albums.firstOrNull()
            AlbumRow(
                name = stringResource(Res.string.all),
                count = null,
                coverUri = first?.coverUri.orEmpty(),
                coverBytes = first?.coverBytes,
                selected = selectedAlbumId == null,
                onClick = { onSelect(null) },
            )
        }
        items(items = albums, key = { it.id }) { album ->
            AlbumRow(
                name = album.name,
                count = album.count,
                coverUri = album.coverUri,
                coverBytes = album.coverBytes,
                selected = album.id == selectedAlbumId,
                onClick = { onSelect(album.id) },
            )
        }
    }
}

@Composable
private fun AlbumRow(
    name: String,
    count: Int?,
    coverUri: String,
    coverBytes: ByteArray?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(AppColors.SearchBG)
        ) {
            // iOS 는 대표 이미지 URI 를 만들 수 없어 미리 디코드한 바이트를 쓴다.
            val model: Any? = coverBytes ?: coverUri.takeIf { it.isNotBlank() }
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = name,
            color = if (selected) AppColors.PrimaryMain else AppColors.Text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = count.toString(),
                color = AppColors.TextSub,
                fontSize = 12.sp,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoGalleryBottomSheetContentPreview() {
    MaterialTheme {
        PhotoGalleryBottomSheetContent(
            photos = List(11) { MultimediaRecentPhoto(id = "$it") },
            selectedIds = listOf("1", "3"),
            onClose = {},
            onSend = {},
            onCameraClick = {},
            onPhotoToggle = {},
            onPhotoDetailClick = {},
        )
    }
}

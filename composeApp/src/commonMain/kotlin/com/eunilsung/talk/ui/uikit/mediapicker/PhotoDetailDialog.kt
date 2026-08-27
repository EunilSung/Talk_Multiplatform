package com.eunilsung.talk.ui.uikit.mediapicker

import org.jetbrains.compose.resources.getString
import multiplatformtalk.composeapp.generated.resources.toast_save_failed
import multiplatformtalk.composeapp.generated.resources.toast_saved_to_gallery
import multiplatformtalk.composeapp.generated.resources.video
import multiplatformtalk.composeapp.generated.resources.photo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.close_icon
import multiplatformtalk.composeapp.generated.resources.ok
import kotlinx.coroutines.launch
import com.eunilsung.talk.data.local.FileMetadataResolver
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.clickable
import com.eunilsung.talk.ui.uikit.toast.ToastMessage
import com.eunilsung.talk.ui.uikit.video.VideoPlayer
import multiplatformtalk.composeapp.generated.resources.download_icon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun PhotoDetailDialog(
    photo: MultimediaRecentPhoto,
    isUserProfileUpload: Boolean = false,
    onUpload: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var inlineToast by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                /** 확대된 이미지가 다이얼로그 밖으로 새어 나가지 않게 자른다. */
                .clipToBounds()
        ) {
            val bytes = photo.thumbnailBytes
            val uri = photo.uri
            when {
                /** 동영상은 플레이어가 자체 제스처를 쓰므로 핀치줌을 걸지 않는다. */
                photo.isVideo && !uri.isNullOrBlank() -> {
                    VideoPlayer(
                        uri = uri,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                bytes != null -> ZoomableImage(model = bytes, modifier = Modifier.fillMaxSize())
                !uri.isNullOrBlank() -> ZoomableImage(model = uri, modifier = Modifier.fillMaxSize())
                else -> Unit
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(BAR_SCRIM)
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    modifier = Modifier.size(28.dp),
                    onClick = onDismiss
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.close_icon),
                        contentDescription = "Close",
                        tint = AppColors.White
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isUserProfileUpload) {
                    Text(
                        text = stringResource(Res.string.ok),
                        modifier = Modifier.clickable(onClick = onUpload),
                        color = AppColors.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else if (photo.serverFileName.isNotBlank()) {
                    val resolver: FileMetadataResolver = koinInject()
                    val scope = rememberCoroutineScope()
                    val saveAs = photo.originalFileName.ifBlank { photo.serverFileName }
                    val isVideo = photo.isVideo

                    IconButton(
                        onClick = {
                            scope.launch {
                                val source = photo.uri?.takeIf { it.isNotBlank() }
                                    ?: photo.serverFileName
                                val saveBytes = runCatching {
                                    resolver.readBytes(source)
                                }.getOrNull()
                                val saved = saveBytes?.let {
                                    runCatching {
                                        if (isVideo) resolver.saveVideoToGallery(saveAs, it)
                                        else resolver.saveToGallery(saveAs, it)
                                    }.getOrNull()
                                }
                                val label = getString(if (isVideo) Res.string.video else Res.string.photo)
                                inlineToast = if (saved != null) {
                                    getString(Res.string.toast_saved_to_gallery, saveAs)
                                } else {
                                    getString(Res.string.toast_save_failed, label)
                                }
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.download_icon),
                            modifier = Modifier.size(25.dp),
                            contentDescription = "Download Icon",
                            tint = AppColors.White
                        )
                    }
                }
            }

            ToastMessage(
                message = inlineToast.orEmpty(),
                isVisible = inlineToast != null,
                onDismiss = { inlineToast = null },
                left = null,
            )
        }
    }
}

/**
 * 상단 바 배경 — 반투명 검정.
 *
 * 확대하면 이미지가 바 영역까지 올라오는데, 바를 불투명하게 덮으면 그만큼 사진이 가려지고
 * 투명하게 두면 밝은 사진 위에서 아이콘이 안 보인다. 그 사이를 두는 값이다.
 */
private val BAR_SCRIM = Color.Black.copy(alpha = 0.5f)

/** 핀치줌 하한 — 원본 크기보다 작게 줄이지는 않는다. */
private const val MIN_SCALE = 1f

/** 핀치줌 상한. 더 키우면 화질이 뭉개져 확대 의미가 없다. */
private const val MAX_SCALE = 5f

/** 더블탭 한 번에 확대되는 배율. */
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * 핀치줌·패닝이 되는 이미지.
 *
 * 배율이 1 이면 이동을 허용하지 않고 위치를 원점으로 되돌린다 — 축소 상태에서 이미지가
 * 화면 밖으로 밀려나 빈 화면만 남는 것을 막는다. 확대 중에는 늘어난 만큼(`크기 × (배율 - 1)`)의
 * 절반까지만 이동할 수 있어 이미지가 완전히 빠져나가지 않는다.
 *
 * [model] 이 바뀌면(다른 사진을 열면) 배율·위치를 초기화한다.
 */
@Composable
private fun ZoomableImage(
    model: Any,
    modifier: Modifier = Modifier,
) {
    var scale by remember(model) { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(target: Offset, currentScale: Float): Offset {
        if (currentScale <= MIN_SCALE) return Offset.Zero
        val maxX = (containerSize.width * (currentScale - 1f)) / 2f
        val maxY = (containerSize.height * (currentScale - 1f)) / 2f
        return Offset(
            x = target.x.coerceIn(-maxX, maxX),
            y = target.y.coerceIn(-maxY, maxY),
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(model) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    scale = next
                    offset = clamp(offset + pan, next)
                }
            }
            .pointerInput(model) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > MIN_SCALE) {
                            scale = MIN_SCALE
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_SCALE
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PhotoDetailDialogPreview() {
    MaterialTheme {
        PhotoDetailDialog(
            photo = MultimediaRecentPhoto(id = "p0", serverFileName = "sample"),
            onDismiss = {}
        )
    }
}

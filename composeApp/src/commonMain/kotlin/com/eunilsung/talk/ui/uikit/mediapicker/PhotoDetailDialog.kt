package com.eunilsung.talk.ui.uikit.mediapicker

import org.jetbrains.compose.resources.getString
import multiplatformtalk.composeapp.generated.resources.toast_save_failed
import multiplatformtalk.composeapp.generated.resources.toast_saved_to_gallery
import multiplatformtalk.composeapp.generated.resources.video
import multiplatformtalk.composeapp.generated.resources.photo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
import com.eunilsung.talk.ui.chatroom.ChatRoomActions
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.clickable
import com.eunilsung.talk.ui.uikit.toast.ToastMessage
import com.eunilsung.talk.ui.uikit.video.VideoPlayer
import multiplatformtalk.composeapp.generated.resources.download_icon
import multiplatformtalk.composeapp.generated.resources.search_icon
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
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
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
                                val bytes = runCatching {
                                    resolver.readBytes(source)
                                }.getOrNull()
                                val saved = bytes?.let {
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val bytes = photo.thumbnailBytes
                val uri = photo.uri
                when {
                    photo.isVideo && !uri.isNullOrBlank() -> {
                        VideoPlayer(
                            uri = uri,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    bytes != null -> {
                        AsyncImage(
                            model = bytes,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    !uri.isNullOrBlank() -> {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    else -> Unit
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

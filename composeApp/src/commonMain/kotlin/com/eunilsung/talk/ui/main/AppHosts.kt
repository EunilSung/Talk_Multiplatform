package com.eunilsung.talk.ui.main

import multiplatformtalk.composeapp.generated.resources.toast_download_failed
import multiplatformtalk.composeapp.generated.resources.toast_file_open_failed
import org.jetbrains.compose.resources.getString
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import com.eunilsung.talk.data.local.FileMetadataResolver
import com.eunilsung.talk.data.local.FileOpener
import com.eunilsung.talk.ui.quickactions.rememberGroupPicker
import com.eunilsung.talk.ui.quickactions.rememberQuickActions
import com.eunilsung.talk.ui.uikit.BackHandler
import com.eunilsung.talk.ui.uikit.dialog.DialogHost
import com.eunilsung.talk.Config
import com.eunilsung.talk.ui.uikit.mediapicker.FileDetailBottomSheetContent
import com.eunilsung.talk.ui.uikit.mediapicker.LocalShowFileDetail
import com.eunilsung.talk.ui.uikit.mediapicker.MultimediaRecentPhoto
import com.eunilsung.talk.ui.uikit.mediapicker.PhotoDetailDialog
import androidx.compose.ui.unit.dp
import com.eunilsung.talk.ui.uikit.sheet.BottomSheetHost
import com.eunilsung.talk.ui.uikit.sheet.LocalBottomSheetManager
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import com.eunilsung.talk.ui.uikit.toast.ToastHost
import com.eunilsung.talk.ui.uikit.pointer.blockPointerInput
import com.eunilsung.talk.ui.uikit.watermark.Watermark
import com.eunilsung.talk.ui.userprofile.LocalShowUserProfile
import com.eunilsung.talk.ui.userprofile.UserProfileBottomSheetContent
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.connection_reconnecting
import com.eunilsung.talk.ui.theme.AppColors
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun AppHosts(
    rightNavigator: Navigator,
    content: @Composable () -> Unit
) {
    var fullScreenOverlay by remember { mutableStateOf<Screen?>(null) }

    CompositionLocalProvider(
        LocalRightNavigator provides rightNavigator,
        LocalFullScreenOverlay provides { screen -> fullScreenOverlay = screen }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            )
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                ConnectionStatusBar()
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    ToastHost {
                        DialogHost {
                            BottomSheetHost {
                                UserProfileSheetBinding {
                                    FileDetailSheetBinding {
                                        content()
                                    }
                                }
                            }
                        }
                        fullScreenOverlay?.let { screen ->
                            BackHandler { fullScreenOverlay = null }
                            // 오버레이는 앱 본문과 같은 Box 의 z-형제라, 입력을 막지 않으면 빈 영역
                            // 탭·드래그가 뒤쪽(대화방·대화함)으로 그대로 전달된다. 화면마다 막는 대신
                            // 호스트에서 한 번 끊어 루트가 Scaffold 가 아닌 화면까지 함께 보호한다.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .blockPointerInput()
                            ) {
                                screen.Content()
                            }
                        }
                    }
                    Watermark()
                }
            }
        }
    }
}

/** 상단 연결상태 배너 — 로그인 상태에서 소켓이 끊기거나 재연결 중이면 표시. */
@Composable
private fun ConnectionStatusBar() {
    val loginRepository: com.eunilsung.talk.domain.repository.LoginRepository = koinInject()
    val isConnected by loginRepository.isConnected.collectAsState()
    val isLoggedIn by loginRepository.isLoggedIn.collectAsState()

    AnimatedVisibility(visible = isLoggedIn && !isConnected) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Line)
                .padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 2.dp,
                color = AppColors.White,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.connection_reconnecting),
                color = AppColors.White,
                fontSize = 11.sp,
                lineHeight = 11.sp
            )
        }
    }
}

@Composable
private fun FileDetailSheetBinding(content: @Composable () -> Unit) {
    val sheet = LocalBottomSheetManager.current
    val toast = LocalToastManager.current
    val fileOpener: FileOpener = koinInject()
    val fileMetadataResolver: FileMetadataResolver = koinInject()
    val scope = rememberCoroutineScope()

    var photoDetail by remember { mutableStateOf<MultimediaRecentPhoto?>(null) }

    CompositionLocalProvider(
        LocalShowFileDetail provides showFileDetail@{ serverFileName, originalFileName, senderLabel ->
            if (serverFileName.isBlank()) return@showFileDetail
            val displayName = originalFileName.ifBlank { serverFileName }
            sheet.custom(bottomPadding = 20.dp) {
                FileDetailBottomSheetContent(
                    fileName = displayName,
                    senderLabel = senderLabel,
                    onClose = { sheet.hide() },
                    onDownload = {
                        scope.launch {
                            val saved = runCatching {
                                saveToDownloads(
                                    source = serverFileName,
                                    displayName = displayName,
                                    resolver = fileMetadataResolver,
                                )
                            }.getOrNull()
                            toast.show(
                                if (saved != null) "'$displayName' 다운로드 완료"
                                else getString(Res.string.toast_download_failed)
                            )
                        }
                    },
                    onOpen = {
                        if (isImageFile(displayName) || isImageFile(serverFileName)) {
                            sheet.hide()
                            photoDetail = MultimediaRecentPhoto(
                                id = serverFileName,
                                uri = serverFileName,
                                serverFileName = serverFileName,
                                originalFileName = displayName,
                            )
                        } else {
                            scope.launch {
                                val existing = runCatching {
                                    fileMetadataResolver.findDownloadedFile(displayName)
                                }.getOrNull()
                                val downloaded = existing == null
                                val path = existing ?: runCatching {
                                    saveToDownloads(
                                        source = serverFileName,
                                        displayName = displayName,
                                        resolver = fileMetadataResolver,
                                    )
                                }.getOrNull()
                                val opened = path?.let {
                                    runCatching { fileOpener.open(it) }.getOrDefault(false)
                                } ?: false
                                when {
                                    !opened -> toast.show(getString(Res.string.toast_file_open_failed))
                                    downloaded -> toast.show("'$displayName' 다운로드 완료")
                                }
                            }
                        }
                    },
                )
            }
        }
    ) {
        content()
        photoDetail?.let { photo ->
            PhotoDetailDialog(
                photo = photo,
                onDismiss = { photoDetail = null },
            )
        }
    }
}

/** 파일을 다운로드 폴더로 복사. 읽을 수 없으면 null. */
private suspend fun saveToDownloads(
    source: String,
    displayName: String,
    resolver: FileMetadataResolver,
): String? {
    val bytes = runCatching { resolver.readBytes(source) }.getOrNull() ?: return null
    return resolver.saveDownloadedFile(displayName, bytes)
}

private fun isImageFile(name: String): Boolean {
    val lower = name.substringAfterLast('.', "").lowercase()
    return lower in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
}

@Composable
private fun UserProfileSheetBinding(content: @Composable () -> Unit) {
    val sheet = LocalBottomSheetManager.current
    val qa = rememberQuickActions()
    val picker = rememberGroupPicker()
    CompositionLocalProvider(
        LocalShowUserProfile provides { id ->
            if (id.isNotBlank()) {
                sheet.custom(bottomPadding = 20.dp) {
                    UserProfileBottomSheetContent(
                        userId = id,
                        onClose = { sheet.hide() },
                        onChat = { p ->
                            sheet.hide()
                            qa.startChat(listOf(p.userId))
                        },
                        onAddToGroup = { p ->
                            picker(p.name.ifBlank { p.userId }) { targetGroupId ->
                                qa.addUserToGroup(p.userId, targetGroupId)
                            }
                        }
                    )
                }
            }
        }
    ) {
        content()
    }
}

package com.eunilsung.talk.ui.userprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_left_icon
import multiplatformtalk.composeapp.generated.resources.profile_photo_select
import multiplatformtalk.composeapp.generated.resources.no_photos
import multiplatformtalk.composeapp.generated.resources.photo_permission_title
import multiplatformtalk.composeapp.generated.resources.photo_permission_message
import multiplatformtalk.composeapp.generated.resources.ok
import multiplatformtalk.composeapp.generated.resources.cancel
import org.jetbrains.compose.resources.stringResource
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.BackHandler
import com.eunilsung.talk.ui.uikit.dialog.Button2Dialog
import com.eunilsung.talk.ui.uikit.mediapicker.MultimediaRecentPhoto
import com.eunilsung.talk.ui.uikit.mediapicker.PhotoDetailDialog
import com.eunilsung.talk.ui.uikit.mediapicker.SelectablePhotoCell
import com.eunilsung.talk.ui.uikit.topbar.TopBarV1
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.collectAsState

/**
 * 프로필 사진 선택 전용 풀스크린 화면 — [LocalFullScreenOverlay] 로 띄움.
 * 사진 탭 → 즉시 [onSelected] 로 해당 uri 전달 후 오버레이 닫힘 (단일 선택).
 */
class ProfilePhotoPickerScreen(
    private val onSelected: (String) -> Unit,
) : Screen {
    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val viewModel: ProfilePhotoPickerViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val showOverlay = LocalFullScreenOverlay.current

        var detail by remember { mutableStateOf<MultimediaRecentPhoto?>(null) }

        BackHandler(enabled = true) { showOverlay(null) }

        LaunchedEffect(viewModel) {
            viewModel.onAction(ProfilePhotoPickerActions.OnStart)
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ProfilePhotoPickerEvent.Saved -> {
                        onSelected(event.path)
                        detail = null
                        showOverlay(null)
                    }
                }
            }
        }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.onAction(ProfilePhotoPickerActions.OnResume)
        }

        val items = remember(uiState.photos) {
            uiState.photos.map { MultimediaRecentPhoto(id = it.id, uri = it.uri, thumbnailBytes = it.thumbnailBytes) }
        }

        Box(modifier = Modifier.fillMaxSize()) {
        ProfilePhotoPickerBody(
            items = items,
            onBack = { showOverlay(null) },
            onPhotoClick = { detail = it },
        )

            if (uiState.isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Black.copy(alpha = 0.4f))
                        .clickable(enabled = false) { },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.Main)
                }
            }
        }

        detail?.let { photo ->
            PhotoDetailDialog(
                photo = photo,
                isUserProfileUpload = true,
                onUpload = {
                    val uri = photo.uri
                    if (!uri.isNullOrBlank()) {
                        detail = null
                        viewModel.onAction(ProfilePhotoPickerActions.OnUpload(uri))
                    }
                },
                onDismiss = { detail = null }
            )
        }

        if (uiState.showPermissionDialog) {
            Button2Dialog(
                onDismissRequest = {
                    viewModel.onAction(ProfilePhotoPickerActions.OnDismissPermission)
                    showOverlay(null)
                },
                title = { Text(stringResource(Res.string.photo_permission_title)) },
                description = {
                    Text(
                        text = stringResource(Res.string.photo_permission_message),
                        color = AppColors.TextSub,
                        fontSize = 14.sp
                    )
                              },
                confirmText = stringResource(Res.string.ok),
                dismissText = stringResource(Res.string.cancel),
                onConfirm = { viewModel.onAction(ProfilePhotoPickerActions.OnRequestPhotoAccess) },
                onDismiss = {
                    viewModel.onAction(ProfilePhotoPickerActions.OnDismissPermission)
                    showOverlay(null)
                },
            )
        }
    }
}

/** 프로필 사진 선택 본문(상단바 + 사진 그리드/빈 상태) — 상태 없는 순수 UI (Preview 가능). */
@Composable
private fun ProfilePhotoPickerBody(
    items: List<MultimediaRecentPhoto>,
    onBack: () -> Unit,
    onPhotoClick: (MultimediaRecentPhoto) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
    ) {
        TopBarV1(
            title = stringResource(Res.string.profile_photo_select),
            navigationIcon = {
                IconButton(modifier = Modifier.size(28.dp), onClick = onBack) {
                    Icon(
                        painter = painterResource(Res.drawable.arrow_left_icon),
                        modifier = Modifier.size(28.dp),
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        )
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.no_photos),
                    style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.TextSub)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = 4.dp, top = 4.dp, end = 4.dp,
                    bottom = 4.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(items = items, key = { it.id }) { photo ->
                    SelectablePhotoCell(
                        photo = photo,
                        selectionOrder = 0,
                        onToggleSelect = { onPhotoClick(photo) },
                        onDetailClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        cornerRadius = 0.dp,
                        isShowToggle = false
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfilePhotoPickerBodyPreview() {
    MaterialTheme {
        ProfilePhotoPickerBody(
            items = List(6) { MultimediaRecentPhoto(id = "$it") },
            onBack = {},
            onPhotoClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfilePhotoPickerBodyEmptyPreview() {
    MaterialTheme {
        ProfilePhotoPickerBody(items = emptyList(), onBack = {}, onPhotoClick = {})
    }
}

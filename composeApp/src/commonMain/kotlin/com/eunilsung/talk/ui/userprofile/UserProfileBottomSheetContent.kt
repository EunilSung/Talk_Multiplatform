package com.eunilsung.talk.ui.userprofile

import multiplatformtalk.composeapp.generated.resources.retry
import multiplatformtalk.composeapp.generated.resources.profile_photo_change
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.call_do
import multiplatformtalk.composeapp.generated.resources.camera2_dark
import multiplatformtalk.composeapp.generated.resources.camera2_light
import multiplatformtalk.composeapp.generated.resources.chat_do
import multiplatformtalk.composeapp.generated.resources.close_icon
import multiplatformtalk.composeapp.generated.resources.contact_do
import multiplatformtalk.composeapp.generated.resources.email
import multiplatformtalk.composeapp.generated.resources.fax
import multiplatformtalk.composeapp.generated.resources.group_do
import multiplatformtalk.composeapp.generated.resources.local_call
import multiplatformtalk.composeapp.generated.resources.move_to_last_chat_icon
import multiplatformtalk.composeapp.generated.resources.phone
import multiplatformtalk.composeapp.generated.resources.profile_call
import multiplatformtalk.composeapp.generated.resources.profile_chat
import multiplatformtalk.composeapp.generated.resources.profile_contact
import multiplatformtalk.composeapp.generated.resources.profile_group
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.UserProfile
import com.eunilsung.talk.ui.chatroom.ChatRoomActions
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.click.itemClickable
import com.eunilsung.talk.ui.uikit.contact.rememberAddContactLauncher
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import com.eunilsung.talk.ui.uikit.image.ProfileImageRefresh
import com.eunilsung.talk.ui.uikit.image.ProfileImages
import com.eunilsung.talk.ui.uikit.image.profileImageUrl
import com.eunilsung.talk.ui.uikit.line.LineDot
import com.eunilsung.talk.ui.uikit.mediapicker.MultimediaRecentPhoto
import com.eunilsung.talk.ui.uikit.mediapicker.PhotoDetailDialog
import com.eunilsung.talk.ui.uikit.phone.rememberPhoneCaller
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import com.eunilsung.talk.ui.userprofile.item.ActionButton
import com.eunilsung.talk.ui.userprofile.item.InfoRow
import com.eunilsung.talk.util.Log
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UserProfileBottomSheetContent(
    userId: String,
    onClose: () -> Unit,
    onChat: (UserProfile) -> Unit = {},
    onAddToGroup: (UserProfile) -> Unit = {}
) {
    val viewModel: UserProfileViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val phoneCaller = rememberPhoneCaller()
    val addContactLauncher = rememberAddContactLauncher()
    val dialog = LocalDialogManager.current
    val toast = LocalToastManager.current
    val callTitle = stringResource(Res.string.call_do)

    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            viewModel.onAction(UserProfileActions.Load(userId))
        }
    }

    val showOverlay = LocalFullScreenOverlay.current

    UserProfileContent(
        uiState = uiState,
        onCameraClick = {
            showOverlay(
                ProfilePhotoPickerScreen(
                    onSelected = { uri -> }
                )
            )
        },
        onAction = { action ->
            val current = (uiState as? UserProfileUiState.Success)?.profile
            when (action) {
                is UserProfileActions.OnClose -> onClose()
                is UserProfileActions.OnChat -> current?.let(onChat)
                is UserProfileActions.OnCall -> current?.let { p ->
                    handleCallAction(
                        profile = p,
                        onPickNumber = phoneCaller,
                        showPickerDialog = { items, onPicked ->
                            dialog.list(title = callTitle, items = items, onSelected = onPicked)
                        },
                        showToast = { toast.show(it) },
                    )
                }
                is UserProfileActions.OnSaveContact -> current?.let { p ->
                    addContactLauncher(p.toContactDraft())
                }
                is UserProfileActions.OnAddToGroup -> current?.let(onAddToGroup)
                else -> viewModel.onAction(action)
            }
        }
    )
}

@Composable
private fun UserProfileContent(
    uiState: UserProfileUiState,
    onAction: (UserProfileActions) -> Unit,
    onCameraClick: () -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxSheetHeight = maxHeight * 0.9f
        val isLandscape = maxWidth > maxHeight

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
        ) {
            when (uiState) {
                is UserProfileUiState.Idle,
                is UserProfileUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is UserProfileUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.TextSub)
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { onAction(UserProfileActions.Retry) }) { Text(stringResource(Res.string.retry)) }
                    }
                }

                is UserProfileUiState.Success -> {
                    ProfileBody(
                        profile = uiState.profile,
                        isLandscape = isLandscape,
                        onAction = onAction,
                        onCameraClick = onCameraClick,
                    )
                }
            }

            IconButton(
                onClick = { onAction(UserProfileActions.OnClose) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 16.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.close_icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

        }
    }
}

private data class ProfileAction(
    val icon: DrawableResource,
    val labelRes: StringResource,
    val action: UserProfileActions,
)

private val PROFILE_ACTIONS = listOf(
    ProfileAction(Res.drawable.profile_chat, Res.string.chat_do, UserProfileActions.OnChat),
    ProfileAction(Res.drawable.profile_call, Res.string.call_do, UserProfileActions.OnCall),
    ProfileAction(Res.drawable.profile_contact, Res.string.contact_do, UserProfileActions.OnSaveContact),
    ProfileAction(Res.drawable.profile_group, Res.string.group_do, UserProfileActions.OnAddToGroup),
)

@Composable
private fun ProfileBody(
    profile: UserProfile,
    isLandscape: Boolean,
    onAction: (UserProfileActions) -> Unit,
    onCameraClick: () -> Unit = {},
) {
    var photoDetailUri by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 50.dp, bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isMe = profile.userId.isNotBlank() &&
                profile.userId.equals(Config.MyInfo.userId, ignoreCase = true)
            Box(modifier = Modifier.size(93.dp)) {
                ProfileImages(
                    userIds = listOf(profile.userId),
                    modifier = Modifier.size(93.dp),
                    roundDp = 30.dp,
                    onClick = {
                        photoDetailUri = profileImageUrl(profile.userId, ProfileImageRefresh.version)
                    }
                )
                if (isMe) {
                    Image(
                        painter = painterResource(
                            if (isSystemInDarkTheme()) Res.drawable.camera2_dark
                            else Res.drawable.camera2_light
                        ),
                        contentDescription = stringResource(Res.string.profile_photo_change),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 6.dp, y = 6.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .itemClickable(cornerRadius = 16.dp, onClick = onCameraClick)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (profile.position.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = profile.position,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = profile.name.ifBlank { profile.userId },
                color = AppColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (profile.department.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = profile.department,
                    color = AppColors.TextSub,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        LineDot(modifier = Modifier.padding(horizontal = 26.dp))
        Spacer(Modifier.height(10.dp))

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PROFILE_ACTIONS.forEach { item ->
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        image = painterResource(item.icon),
                        label = stringResource(item.labelRes),
                        onClick = { onAction(item.action) }
                    )
                }
            }
        } else {
            PROFILE_ACTIONS.chunked(3).forEachIndexed { index, rowItems ->
                if (index > 0) Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        ActionButton(
                            modifier = Modifier.weight(1f),
                            image = painterResource(item.icon),
                            label = stringResource(item.labelRes),
                            onClick = { onAction(item.action) }
                        )
                    }
                    repeat(3 - rowItems.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        InfoRow(label = stringResource(Res.string.local_call), value = profile.extensionNum.ifBlank { profile.localCallNum })
        InfoRow(label = stringResource(Res.string.phone), value = profile.mobileNum)
        InfoRow(label = stringResource(Res.string.email), value = profile.email)
        InfoRow(label = stringResource(Res.string.fax), value = profile.faxNum)
    }

    photoDetailUri?.let { uri ->
        PhotoDetailDialog(
            photo = MultimediaRecentPhoto(
                id = profile.userId,
                uri = uri,
            ),
            onDismiss = { photoDetailUri = null },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun UserProfileBottomSheetContentPreview() {
    MaterialTheme {
        UserProfileContent(
            uiState = UserProfileUiState.Success(
                UserProfile(
                    userId = "",
                    name = "성은일",
                    position = "팀장",
                    department = "개발팀",
                    email = "user@example.com",
                )
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UserProfileLoadingPreview() {
    MaterialTheme {
        UserProfileContent(uiState = UserProfileUiState.Loading, onAction = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun UserProfileErrorPreview() {
    MaterialTheme {
        UserProfileContent(uiState = UserProfileUiState.Error("프로필을 불러오지 못했습니다."), onAction = {})
    }
}

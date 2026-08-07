package com.eunilsung.talk.ui.chatroom.item

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.noti_chat
import multiplatformtalk.composeapp.generated.resources.reply_icon
import org.jetbrains.compose.resources.painterResource
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.Emoticon
import com.eunilsung.talk.domain.model.GroupedChat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.image.NotificationAvatar
import com.eunilsung.talk.ui.uikit.image.ProfileImages
import com.eunilsung.talk.ui.userprofile.LocalShowUserProfile

data class ChatItemProps(
    val chat: Chat.Item = Chat.Item(),
    val searchQuery: String = "",
    val maxBubbleWidth: Dp = Dp.Unspecified,
    val isMe: Boolean = false,
    val chatFontSize: TextUnit = 13.sp,
    val showTime: Boolean = false,
    val isBookmarked: Boolean = false,
    /** 말풍선 옆 시간·읽음수 슬롯 표시 — 목록 위 날짜 구분선(GroupedChat.showDate)과 별개다. */
    val showTimeSlot: Boolean = true,
    val onImageClick: () -> Unit = {},
    val onFileClick: () -> Unit = {},
    val onLongClick: () -> Unit = {},
    val onResend: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onReplyOriginClick: () -> Unit = {},
    val onVoteClick: () -> Unit = {},
    val onNoticeClick: () -> Unit = {},
)

@Composable
fun ChatItem(
    groupedItem: GroupedChat,
    searchQuery: String = "",
    searchedUserName: String = "",
    chatFontSize: TextUnit = 13.sp,
    isBookmarked: Boolean = false,
    onSwipeReply: (Chat.Item) -> Unit = {},
    onImageClick: (Chat.Item) -> Unit = {},
    onResendFailedChat: (Chat.Item) -> Unit = {},
    onDeleteFailedChat: (Chat.Item) -> Unit = {},
    onLongClick: (Chat.Item) -> Unit = {},
    onFileClick: (Chat.Item) -> Unit = {},
    onReplyOriginClick: (originChatId: String) -> Unit = {},
    onVoteClick: (Chat.Item) -> Unit = {},
    onNoticeClick: (Chat.Item) -> Unit = {},
    onEmpathyClick: (Chat.Item, typeIndex: Int) -> Unit = { _, _ -> },
    onEmpathyLongClick: (Chat.Item, typeIndex: Int) -> Unit = { _, _ -> },
) {
    val showUserProfile = LocalShowUserProfile.current
    val chat = groupedItem.chat
    val isMe = chat.isMe
    val keyboardController = LocalSoftwareKeyboardController.current

    if (chat.chatType == Chat.Type.INVITE) {
        InviteItem(chat = chat)
        return
    }
    if (chat.chatType == Chat.Type.EXIT) {
        ExitItem(chat = chat)
        return
    }
    val canInteract = chat.chatStatue == Chat.Statue.COMPLETE
    val canReply = !chat.isRecalled && canInteract
    val canEmpathy = !chat.isRecalled && canInteract

    // 최소 높이는 프로필 이미지가 그려지는 행에만 적용.
    val showsProfileImage = !isMe && groupedItem.showProfileAndName

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (showsProfileImage) Modifier.heightIn(min = 45.dp) else Modifier)
            .padding(horizontal = 10.dp)
    ) {
        if (!isMe){
            if (groupedItem.showProfileAndName) {
                if (chat.user.id.contains("$")) {
                    NotificationAvatar()
                }else{
                    ProfileImages(userIds = listOf(chat.user.id), onClick = {
                        keyboardController?.hide()
                        showUserProfile(chat.user.id)
                    })
                }
            }else{
                Spacer(modifier = Modifier.width(50.dp))
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 5.dp),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            if (!isMe){
                if (groupedItem.showProfileAndName) {
                    UserNameRow(
                        name = chat.user.name,
                        searchedUserName = searchedUserName
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val maxBubbleWidth = maxWidth * 0.75f
                val density = LocalDensity.current
                val thresholdPx = with(density) { 80.dp.toPx() }
                var dragOffset by remember(chat.chatID) { mutableFloatStateOf(0f) }
                var isDragging by remember(chat.chatID) { mutableStateOf(false) }
                val itemProps = ChatItemProps(
                    chat = chat,
                    searchQuery = searchQuery,
                    maxBubbleWidth = maxBubbleWidth,
                    isMe = isMe,
                    chatFontSize = chatFontSize,
                    showTime = groupedItem.showTime,
                    isBookmarked = isBookmarked,
                    onImageClick = if (canInteract) { { onImageClick(chat) } } else { {} },
                    onFileClick = if (canInteract) { { onFileClick(chat) } } else { {} },
                    onLongClick = if (canInteract) { { onLongClick(chat) } } else { {} },
                    onResend = { onResendFailedChat(chat) },
                    onDelete = { onDeleteFailedChat(chat) },
                    onReplyOriginClick = run {
                        val originId = chat.replyChat.chatID
                        if (canInteract && originId.isNotBlank()) {
                            { onReplyOriginClick(originId) }
                        } else { {} }
                    },
                    onVoteClick = { onVoteClick(chat) },
                    onNoticeClick = { onNoticeClick(chat) },
                )

                LaunchedEffect(isDragging, canReply) {
                    if (!canReply || isDragging || dragOffset == 0f) return@LaunchedEffect
                    if (dragOffset < -thresholdPx) onSwipeReply(chat)
                    animate(
                        initialValue = dragOffset,
                        targetValue = 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) { v, _ -> dragOffset = v }
                }

                if (!canReply) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        ChatRow(itemProps)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(chat.chatID) {
                                val slop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var replying = false
                                    var decided = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        if (!change.pressed) break
                                        if (!decided) {
                                            if (change.isConsumed) break
                                            val dx = change.position.x - down.position.x
                                            val dy = change.position.y - down.position.y
                                            if (kotlin.math.abs(dx) < slop &&
                                                kotlin.math.abs(dy) < slop
                                            ) continue
                                            decided = true
                                            if (kotlin.math.abs(dy) >= kotlin.math.abs(dx) || dx > 0) break
                                            replying = true
                                            isDragging = true
                                        }
                                        if (replying) {
                                            dragOffset = (dragOffset + change.positionChange().x)
                                                .coerceAtMost(0f)
                                            change.consume()
                                        }
                                    }
                                    if (replying) isDragging = false
                                }
                            }
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.reply_icon),
                            contentDescription = "Swipe to reply",
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .size(20.dp)
                                .graphicsLayer {
                                    alpha = (-dragOffset / thresholdPx).coerceIn(0f, 1f)
                                }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(dragOffset.toInt(), 0) },
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            ChatRow(itemProps)
                        }
                    }
                }
            }

            if (canEmpathy) EmpathyItem(
                chat = chat,
                onEmpathyClick = { idx -> onEmpathyClick(chat, idx) },
                onEmpathyLongClick = { idx -> onEmpathyLongClick(chat, idx) },
            )
        }
    }
}

@Composable
private fun UserNameRow(
    name: String,
    searchedUserName: String,
){
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = buildSearchHighlightedText(text = name, searchWord = searchedUserName),
            color = AppColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


internal val ChatItemProps.bubbleBgColor: Color
    @Composable get() = if (isMe) AppColors.ChatMeBg else AppColors.ChatOtherBg

internal val ChatItemProps.bubbleTextColor: Color
    @Composable get() = if (isMe) AppColors.White else AppColors.Text

internal fun ChatItemProps.bubbleShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = if (isMe) 5.dp else 0.dp,
    topEnd = if (isMe) 0.dp else 5.dp,
    bottomStart = 5.dp,
    bottomEnd = 5.dp,
)

internal fun ChatItemProps.bubbleWidthCap(): Modifier =
    if (maxBubbleWidth != Dp.Unspecified) Modifier.widthIn(max = maxBubbleWidth) else Modifier

@Composable
internal fun ChatBubbleRow(
    itemProps: ChatItemProps,
    modifier: Modifier = Modifier,
    bubble: @Composable () -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        if (itemProps.isMe && itemProps.showTimeSlot) ChatDateSlot(itemProps)
        bubble()
        if (!itemProps.isMe && itemProps.showTimeSlot) ChatDateSlot(itemProps)
    }
}

@Composable
private fun ChatRow(itemProps: ChatItemProps) {
    Column(
        horizontalAlignment = if (itemProps.isMe) Alignment.End else Alignment.Start
    ) {
        if (itemProps.chat.isRecalled) {
            RecallItem(chat = itemProps.chat)
            return@Column
        }

        when(itemProps.chat.chatType){
            Chat.Type.ALARM -> {
                TextItem(itemProps)
            }
            Chat.Type.IMAGE -> {
                ImageItem(itemProps)
            }
            Chat.Type.VIDEO -> {
                VideoItem(itemProps)
            }
            Chat.Type.FILE -> {
                FileItem(itemProps)
            }
            Chat.Type.VOTE -> {
                VoteItem(itemProps)
            }
            Chat.Type.VOTE_COMPLETE -> {
                VoteCompleteItem(itemProps)
            }
            Chat.Type.NOTICE -> {
                NoticeItem(itemProps)
            }
            Chat.Type.EMOTICON -> {
                val hasText = itemProps.chat.chatContent.isNotEmpty()
                ChatEmoticonItem(if (hasText) itemProps.copy(showTimeSlot = false) else itemProps)
                if (hasText){
                    Spacer(modifier = Modifier.size(5.dp))
                    TextItem(itemProps)
                }
            }
            Chat.Type.REPLY -> {
                val hasEmoticon = itemProps.chat.emoticon.id.isNotEmpty()
                ReplyItem(if (hasEmoticon) itemProps.copy(showTimeSlot = false) else itemProps)
                if (hasEmoticon){
                    Spacer(modifier = Modifier.size(5.dp))
                    ChatEmoticonItem(itemProps)
                }
            }
            Chat.Type.TEXT -> {
                TextItem(itemProps)
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun ChatItemPreview() {
    ChatItem(
        GroupedChat(
            chat = Chat.Item(
                user = User(
                    id = "me",
                    name = "철수"
                ),
                chatContent = "안녕하세요 가나다라마바사아자차카타파아 아에이오우이우",
                date = "2026-01-01 00:00:00:000",
                emoticon = Emoticon(
                    id = "(피기_알겠습니다)"
                )
            ),
            showProfileAndName = true,
            showDate = true,
            showTime = true,
        )
    )
}
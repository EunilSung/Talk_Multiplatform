package com.eunilsung.talk.ui.chatroom

import androidx.compose.runtime.Composable
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.Notice
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.chatroom.item.ChatRoomDrawerContent
import com.eunilsung.talk.ui.chatroom.vote.VoteMode
import com.eunilsung.talk.ui.chatroom.vote.VoteScreen
import com.eunilsung.talk.ui.invite.InviteMode
import com.eunilsung.talk.ui.invite.InviteScreen
import com.eunilsung.talk.ui.main.LocalFullScreenOverlay
import com.eunilsung.talk.ui.uikit.drawer.SideDrawer
import com.eunilsung.talk.ui.uikit.toast.LocalToastManager
import com.eunilsung.talk.ui.userprofile.LocalShowUserProfile

/**
 * 대화방 오른쪽 서랍 — 투표·공지·초대와 참여자 목록. 어느 항목을 누르든 서랍은 닫힌다.
 *
 * @param onShowNotice 걸린 공지의 본문을 전문 보기로 띄운다. 공지가 없으면 부르지 않고 안내만 한다.
 */
@Composable
internal fun ChatRoomDrawer(
    isOpen: Boolean,
    chatRoom: ChatRoom.Item?,
    currentChatRoomId: String,
    users: List<User>,
    notice: Notice?,
    strings: ChatRoomStrings,
    onDismiss: () -> Unit,
    onShowNotice: (content: String) -> Unit,
) {
    val fullScreenOverlay = LocalFullScreenOverlay.current
    val toastManager = LocalToastManager.current

    SideDrawer(
        isVisible = isOpen,
        onDismiss = onDismiss
    ) {
        val showUserProfile = LocalShowUserProfile.current
        ChatRoomDrawerContent(
            users = users,
            onVoteClick = {
                onDismiss()
                fullScreenOverlay(
                    VoteScreen(
                        chatRoomId = currentChatRoomId,
                        initialMode = VoteMode.LIST,
                    )
                )
            },
            onNoticeClick = {
                onDismiss()
                val content = notice?.content
                if (content?.isNotBlank() == true) {
                    onShowNotice(content)
                } else {
                    toastManager.show(strings.noNotice)
                }
            },
            onInviteClick = {
                onDismiss()
                fullScreenOverlay(
                    InviteScreen(
                        InviteMode.InviteToChatRoom(
                            chatRoomId = currentChatRoomId,
                            existingUserIds = users.map { it.id },
                            createNewRoom = chatRoom?.isDirectRoom == true,
                            existingUsers = users.map { it.id to it.name },
                        )
                    )
                )
            },
            onUserClick = { showUserProfile(it) },
        )
    }
}

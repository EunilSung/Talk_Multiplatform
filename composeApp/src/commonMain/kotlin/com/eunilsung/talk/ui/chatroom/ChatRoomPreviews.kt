package com.eunilsung.talk.ui.chatroom

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.uikit.emoticon.EmoticonItem
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.emoticon_tab_face_human_down

@Preview(showBackground = true)
@Composable
private fun ChatRoomScreenPreview() {
    MaterialTheme {
        ChatRoomContent(
            uiState = ChatRoomUiState(
                chatRoom = ChatRoom.Item(),
                messages = ChatMessagesUiState.Success(groupedChats = emptyList()),
            ),
            onAction = {}
        )
    }
}

private val previewUsers = listOf(
    User(id = "1", name = "성은일", departmentName = "개발팀", positionName = "팀장"),
    User(id = "2", name = "홍길동", departmentName = "기획팀"),
    User(id = "3", name = "김철수"),
)

@Preview(showBackground = true)
@Composable
private fun ChatRoomSearchModePreview() {
    MaterialTheme {
        ChatRoomContent(
            uiState = ChatRoomUiState(
                chatRoom = ChatRoom.Item(),
                messages = ChatMessagesUiState.Success(groupedChats = emptyList()),
                users = previewUsers,
                search = ChatSearchUiState(
                    isActive = true,
                    query = "검색어",
                    matchedChatIds = listOf("a", "b", "c", "d", "e"),
                    currentIndex = 1,
                ),
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatRoomReplyModePreview() {
    MaterialTheme {
        ChatRoomContent(
            uiState = ChatRoomUiState(
                chatRoom = ChatRoom.Item(),
                messages = ChatMessagesUiState.Success(groupedChats = emptyList()),
                users = previewUsers,
                replyTarget = Chat.Item(
                    user = User(name = "성은일"),
                    chatContent = "답장 대상 본문입니다.",
                ),
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatRoomEmoticonPreviewPreview() {
    MaterialTheme {
        ChatRoomContent(
            uiState = ChatRoomUiState(
                chatRoom = ChatRoom.Item(),
                messages = ChatMessagesUiState.Success(groupedChats = emptyList()),
                users = previewUsers,
            ),
            initialSelectedEmoticon = EmoticonItem(
                id = "sample",
                resource = Res.drawable.emoticon_tab_face_human_down,
                resourcePath = "drawable/emoticon_tab_face_human_down.png",
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatRoomDrawerPreview() {
    MaterialTheme {
        ChatRoomContent(
            uiState = ChatRoomUiState(
                chatRoom = ChatRoom.Item(),
                messages = ChatMessagesUiState.Success(groupedChats = emptyList()),
                users = previewUsers,
            ),
            initialDrawerOpen = true,
            onAction = {},
        )
    }
}

package com.eunilsung.talk.ui.quickactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.repository.AddUserResult
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases
import com.eunilsung.talk.domain.usecase.GroupUseCases
import com.eunilsung.talk.domain.usecase.InviteUseCases
import com.eunilsung.talk.domain.usecase.UserProfileUseCases
import com.eunilsung.talk.util.UserListCodec

/** 리스트 항목(사용자/그룹) 대상 빠른 액션 — 1:1 대화 시작, 그룹에 넣기, 그룹 고르기 목록. */
class QuickActionsViewModel(
    private val groupUseCases: GroupUseCases,
    private val chatRoomListUseCases: ChatRoomListUseCases,
    private val inviteUseCases: InviteUseCases,
    private val userProfileUseCases: UserProfileUseCases,
) : ViewModel() {

    val uiState: StateFlow<QuickActionsUiState> =
        groupUseCases.getGroups()
            .map { QuickActionsUiState(groups = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QuickActionsUiState())

    private val _events = Channel<QuickActionsEvent>(Channel.BUFFERED)
    val events: Flow<QuickActionsEvent> = _events.receiveAsFlow()

    fun onAction(action: QuickActionsActions) {
        when (action) {
            is QuickActionsActions.StartChat -> startChat(action.targetUserIds)
            is QuickActionsActions.AddUserToGroup -> addUserToGroup(action.userId, action.targetGroupId)
        }
    }

    private fun startChat(targetUserIds: List<String>) {
        val cleaned = targetUserIds.filter { it.isNotBlank() }.distinct()
        if (cleaned.size != 1) {
            return
        }
        val targetId = cleaned.first()
        val myId = Config.MyInfo.userId

        viewModelScope.launch {
            val existing = findOneToOneChatRoom(targetUserId = targetId, myId = myId)
            if (existing != null) {
                _events.send(QuickActionsEvent.OpenChatRoom(existing.id))
                return@launch
            }

            val name = resolveUserName(targetId)
            val newRoomId = runCatching {
                inviteUseCases.inviteUsers(
                    chatRoomId = "",
                    invitedUsers = listOf(targetId to name),
                    existingUserCount = 0,
                )
            }.getOrNull()

            if (newRoomId.isNullOrBlank()) {
                _events.send(QuickActionsEvent.ChatCreateFailed)
                return@launch
            }
            _events.send(QuickActionsEvent.OpenChatRoom(newRoomId))
        }
    }

    /** 기존 1:1 대화방 검색 — 로컬 대화방 리스트 스냅샷에서 조건 매칭. */
    private suspend fun findOneToOneChatRoom(
        targetUserId: String,
        myId: String,
    ): ChatRoom.Item? {
        val rooms = runCatching { chatRoomListUseCases.getChatRooms().first() }
            .getOrElse { emptyList() }
        if (rooms.isEmpty()) return null

        val expectedCount = if (targetUserId == myId) "1" else "2"
        return rooms.firstOrNull { room ->
            room.totalUserCount == expectedCount
                    && !room.id.contains("$")
                    && UserListCodec.contains(room.totalUserList, targetUserId)
        }
    }

    /** userId 로 사용자 이름 조회 — 캐시 우선, 없으면 프로필 조회, 실패 시 빈 문자열. */
    private suspend fun resolveUserName(userId: String): String {
        val cached = runCatching { userProfileUseCases.getCachedProfile(userId) }.getOrNull()
        if (cached != null && cached.name.isNotBlank()) return cached.name
        val fetched = runCatching { userProfileUseCases.fetchProfile(userId) }
            .getOrNull()?.getOrNull()
        return fetched?.name.orEmpty()
    }

    private fun addUserToGroup(userId: String, targetGroupId: String) {
        viewModelScope.launch {
            val event = when (groupUseCases.copyUserToGroup(userId, targetGroupId)) {
                AddUserResult.SUCCESS -> {
                    val groups = groupUseCases.getGroups().first()
                    QuickActionsEvent.AddedToGroup(groups.firstOrNull { it.id == targetGroupId })
                }
                AddUserResult.ALREADY_EXISTS -> QuickActionsEvent.AlreadyInGroup
                AddUserResult.FAILED -> QuickActionsEvent.AddToGroupFailed
            }
            _events.send(event)
        }
    }
}

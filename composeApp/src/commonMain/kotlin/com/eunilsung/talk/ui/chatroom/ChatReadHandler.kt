package com.eunilsung.talk.ui.chatroom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases
import com.eunilsung.talk.domain.usecase.ChatRoomStateUseCases
import com.eunilsung.talk.domain.usecase.ChatRoomUseCases
import com.eunilsung.talk.util.Log

/**
 * 대화방 VM 의 읽음 부분 — 안 읽은 첫 대화 표시선, 방에 들어올 때의 스크롤 위치, 화면에 띄운 방의 읽음 처리.
 *
 * 표시선은 기기 DB 의 대화가 서버의 마지막 대화까지 맞춰진 뒤에만 세운다. 중간이 비어 있는 채로 세우면
 * 안읽음 수만큼 거슬러 올라간 자리가 실제 첫 안읽음 대화가 아니다.
 *
 * @param currentRoomId 지금 열려 있는 방. 방이 바뀐 뒤에 끝난 일은 결과를 쓰지 않는다.
 */
internal class ChatReadHandler(
    private val scope: CoroutineScope,
    private val chatRoomUseCases: ChatRoomUseCases,
    private val chatRoomListUseCases: ChatRoomListUseCases,
    private val stateUseCases: ChatRoomStateUseCases,
    private val currentRoomId: () -> String,
) {
    private val _firstUnreadChatId = MutableStateFlow<String?>(null)

    /** 안 읽은 첫 대화. 표시선을 그 위에 세운다. 없거나 아직 모르면 null. */
    val firstUnreadChatId: StateFlow<String?> = _firstUnreadChatId.asStateFlow()

    private val _entryScroll = MutableSharedFlow<String?>(replay = 1, extraBufferCapacity = 1)

    /** 방에 들어왔을 때 스크롤할 대화. null 이면 맨 아래. */
    val entryScroll: SharedFlow<String?> = _entryScroll

    /** 다른 방을 열 때 앞 방의 표시선을 지운다. */
    fun reset() {
        _firstUnreadChatId.value = null
    }

    /**
     * 방을 열었을 때 — 서버와 대화를 맞추고, 표시선을 세우고, 진입 스크롤을 알리고, 화면에 떠 있으면
     * 마지막 대화까지 읽은 것으로 보낸다.
     *
     * 기기 DB 가 이미 서버의 마지막 대화까지 갖고 있으면 받아 오기를 기다리지 않고 표시선과 스크롤을 먼저 낸다.
     *
     * @param onFailure 서버와 맞추지 못했을 때.
     */
    fun syncOnEnter(chatRoomId: String, onFailure: (Throwable) -> Unit) {
        scope.launch {
            runCatching {
                var entryEmitted = false

                if (_firstUnreadChatId.value == null && currentRoomId() == chatRoomId) {
                    val room = chatRoomListUseCases.getChatRooms()
                        .first()
                        .firstOrNull { it.id == chatRoomId }
                    val unread = room?.unReadCount?.toIntOrNull() ?: 0
                    val serverLastChatId = room?.lastChatID.orEmpty()
                    if (unread > 0 && serverLastChatId.isNotBlank()) {
                        val cached = chatRoomUseCases.getChats(chatRoomId).first()
                        if (cached.lastOrNull()?.chatID == serverLastChatId && cached.size >= unread) {
                            _firstUnreadChatId.value = cached[cached.size - unread].chatID
                            _entryScroll.tryEmit(_firstUnreadChatId.value)
                            entryEmitted = true
                        }
                    }
                }

                chatRoomUseCases.fetchChats(chatRoomId)
                chatRoomUseCases.fetchNewerChats(chatRoomId)

                if (_firstUnreadChatId.value == null && currentRoomId() == chatRoomId) {
                    val room = chatRoomListUseCases.getChatRooms()
                        .first()
                        .firstOrNull { it.id == chatRoomId }
                    val unread = room?.unReadCount?.toIntOrNull() ?: 0
                    val serverLastChatId = room?.lastChatID.orEmpty()
                    if (unread > 0) {
                        var allChats = chatRoomUseCases.getChats(chatRoomId).first()

                        if (serverLastChatId.isNotBlank() &&
                            allChats.lastOrNull()?.chatID != serverLastChatId
                        ) {
                            runCatching { chatRoomUseCases.fetchChats(chatRoomId) }
                            runCatching { chatRoomUseCases.fetchNewerChats(chatRoomId) }
                            allChats = chatRoomUseCases.getChats(chatRoomId).first()
                        }

                        val synced = serverLastChatId.isBlank() ||
                            allChats.lastOrNull()?.chatID == serverLastChatId
                        if (synced && allChats.size >= unread) {
                            _firstUnreadChatId.value = allChats[allChats.size - unread].chatID
                        } else {
                            Log.message(
                                "[UnreadMarker] skip — not synced(synced=$synced " +
                                    "localLast=${allChats.lastOrNull()?.chatID?.takeLast(8)} " +
                                    "serverLast=${serverLastChatId.takeLast(8)}) size=${allChats.size} unread=$unread"
                            )
                        }
                    }
                }

                if (!entryEmitted && currentRoomId() == chatRoomId) {
                    _entryScroll.tryEmit(_firstUnreadChatId.value)
                }

                if (currentRoomId() == chatRoomId &&
                    stateUseCases.isCurrentChatRoom(chatRoomId)
                ) {
                    val lastChatId = lastSettledChatId(chatRoomId)
                    if (lastChatId.isNotBlank()) {
                        chatRoomUseCases.markChatAsRead(chatRoomId, lastChatId)
                    }
                }
            }.onFailure(onFailure)
        }
    }

    /** 방이 화면에 떠 있는 동안 상대의 새 대화가 오면 그 대화까지 읽은 것으로 보낸다. */
    fun markReadOnNewChat(chatRoomId: String) {
        scope.launch {
            stateUseCases.observeNewChat()
                .filter { it == chatRoomId && currentRoomId() == chatRoomId }
                .collect {
                    if (!stateUseCases.isCurrentChatRoom(chatRoomId)) return@collect

                    val lastChatId = lastSettledChatId(chatRoomId)
                    if (lastChatId.isNotBlank()) {
                        runCatching { chatRoomUseCases.markChatAsRead(chatRoomId, lastChatId) }
                    }
                }
        }
    }

    /** 전송 중이거나 실패한 내 대화를 뺀 마지막 대화. 없으면 빈 문자열. */
    private suspend fun lastSettledChatId(chatRoomId: String): String =
        chatRoomUseCases.getChats(chatRoomId)
            .first()
            .lastOrNull { it.chatStatus != Chat.Status.SENDING && it.chatStatus != Chat.Status.FAIL }
            ?.chatID
            .orEmpty()
}

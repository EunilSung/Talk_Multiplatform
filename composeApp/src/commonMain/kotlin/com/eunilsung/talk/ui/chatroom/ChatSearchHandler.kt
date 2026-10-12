package com.eunilsung.talk.ui.chatroom

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.usecase.ChatRoomUseCases
import com.eunilsung.talk.util.Log

/**
 * 대화방 VM 의 대화 찾기 부분 — 검색어·사람·날짜로 찾기, 찾은 대화 사이 오가기, 특정 대화로 옮겨 가기.
 *
 * 상태는 [ChatRoomUiState.search] 에 쓴다. 대화 목록은 이 상태를 보고 찾은 대화를 표시한다.
 *
 * @param currentRoomId 지금 열려 있는 방. 방이 바뀐 뒤에 돌아온 결과는 버린다.
 * @param loadedChats 지금 기기 DB 에 받아 둔 대화. 없는 대화로 옮겨 가려면 그 주변을 먼저 받아 온다.
 */
internal class ChatSearchHandler(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<ChatRoomUiState>,
    private val chatRoomUseCases: ChatRoomUseCases,
    private val currentRoomId: () -> String,
    private val loadedChats: () -> List<Chat.Item>,
) {
    fun update(transform: (ChatSearchState) -> ChatSearchState) {
        uiState.update { it.copy(search = transform(it.search)) }
    }

    fun updateQuery(query: String) {
        update { it.copy(query = query) }
        if (query.isEmpty()) {
            search()
        }
    }

    /** 입력한 검색어로 찾는다. 날짜로 찾던 중이었으면 날짜 조건은 푼다. */
    fun searchByQuery() {
        update { it.copy(searchedDate = null) }
        search()
    }

    fun selectUser(user: User) {
        update { it.copy(searchedUser = user, searchedDate = null) }
        search()
    }

    fun removeUser() {
        update { it.copy(searchedUser = null) }
        search()
    }

    private fun search() {
        val roomId = currentRoomId()
        if (roomId.isBlank()) return
        val state = uiState.value.search
        val q = state.query
        val u = state.searchedUser
        val d = state.searchedDate

        if (q.isEmpty() && u == null && d.isNullOrBlank()) {
            update {
                it.copy(
                    committedQuery = "",
                    matchedChatIds = emptyList(),
                    currentIndex = -1,
                    focusChatId = null
                )
            }
            return
        }

        update { it.copy(committedQuery = q) }

        scope.launch {
            val dateFrom = if (d.isNullOrBlank()) "" else "$d 00:00:00:000"
            val dateTo   = if (d.isNullOrBlank()) "" else "$d 23:59:59:999"

            val rawDesc = runCatching {
                chatRoomUseCases.searchChats(
                    chatRoomId = roomId,
                    query = q,
                    userId = u?.id.orEmpty(),
                    dateFrom = dateFrom,
                    dateTo = dateTo
                )
            }.getOrElse { emptyList() }

            val idsAsc = rawDesc.sortedBy { it.second }.map { it.first }

            if (idsAsc.isEmpty()) {
                update {
                    it.copy(
                        matchedChatIds = emptyList(),
                        currentIndex = -1,
                        focusChatId = null
                    )
                }
                return@launch
            }

            val focusId = idsAsc.last()
            update {
                it.copy(
                    matchedChatIds = idsAsc,
                    currentIndex = idsAsc.lastIndex,
                )
            }
            focusOnChat(focusId)
        }
    }

    fun searchByDate(datePrefix: String) {
        if (datePrefix.isBlank()) return
        update {
            it.copy(
                query = "",
                searchedUser = null,
                searchedDate = datePrefix,
                matchedChatIds = emptyList(),
                currentIndex = -1,
                focusChatId = null
            )
        }
        search()
    }

    fun moveIndex(delta: Int) {
        val state = uiState.value.search
        if (state.matchedChatIds.isEmpty()) return
        val newIndex = (state.currentIndex + delta)
            .coerceIn(0, state.matchedChatIds.lastIndex)
        if (newIndex == state.currentIndex) return
        val targetId = state.matchedChatIds[newIndex]

        update { it.copy(currentIndex = newIndex) }
        focusOnChat(targetId)
    }

    /** 그 대화로 옮겨 간다. 받아 두지 않은 대화면 그 주변을 먼저 받아 온다. */
    fun focusOnChat(chatId: String) {
        val roomId = currentRoomId()
        if (roomId.isBlank() || chatId.isBlank()) return
        scope.launch {
            if (loadedChats().none { it.chatID == chatId }) {
                runCatching { chatRoomUseCases.loadChatWithContext(roomId, chatId) }
                    .onFailure { Log.message("[Focus] loadChatWithContext failed: ${it.message}") }
                if (currentRoomId() != roomId) return@launch
            }
            update {
                it.copy(
                    focusChatId = chatId,
                    focusTriggerKey = it.focusTriggerKey + 1,
                )
            }
        }
    }
}

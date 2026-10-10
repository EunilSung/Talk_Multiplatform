package com.eunilsung.talk.domain.usecase

import kotlinx.coroutines.flow.Flow
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.repository.ChatRoomRepository

/** Chat 도메인 UseCase 번들. */
data class ChatRoomUseCases(
    val getChats: GetChatsUseCase,
    val fetchChats: FetchChatsUseCase,
    val fetchMoreChats: FetchMoreChatsUseCase,
    val fetchNewerChats: FetchNewerChatsUseCase,
    val fetchChatRoomUsers: FetchChatRoomUsersUseCase,
    val searchChats: SearchChatsUseCase,
    val loadChatWithContext: LoadChatWithContextUseCase,
    val sendTextChat: SendTextChatUseCase,
    val resendFailedChat: ResendFailedChatUseCase,
    val deleteFailedChat: DeleteFailedChatUseCase,
    val sendEmpathy: SendEmpathyUseCase,
    val recallChat: RecallChatUseCase,
    val loadLatestChats: LoadLatestChatsUseCase,
    val sendFile: SendFileUseCase,
    val markChatAsRead: MarkChatAsReadUseCase,
    val refreshChatUnreadCounts: RefreshChatUnreadCountsUseCase,
    val addNotice: AddNoticeUseCase,
    val deleteNotice: DeleteNoticeUseCase,
    val requestNotice: RequestNoticeUseCase,
    val summarizeUnread: SummarizeUnreadUseCase,
    val fetchBookmarks: FetchBookmarksUseCase,
    val addBookmark: AddBookmarkUseCase,
    val deleteBookmark: DeleteBookmarkUseCase,
)

/** 대화를 공지로 등록. */
class AddNoticeUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String, content: String) =
        repository.addNotice(chatRoomId, content)
}

/** 현재 공지 해제. */
class DeleteNoticeUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String) = repository.deleteNotice(chatRoomId)
}

/** 안읽은 대화 요약. */
class SummarizeUnreadUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String, afterChatId: String?) =
        repository.summarizeUnread(chatRoomId, afterChatId)
}

/** 방 진입 시 공지 조회. */
class RequestNoticeUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String) = repository.requestNotice(chatRoomId)
}

/** 책갈피 목록 조회. */
class FetchBookmarksUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String) = repository.fetchBookmarks(chatRoomId)
}

/** 대화를 책갈피에 추가. */
class AddBookmarkUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String, chat: Chat.Item) =
        repository.addBookmark(chatRoomId, chat)
}

/** 책갈피 해제. */
class DeleteBookmarkUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String, chatId: String) =
        repository.deleteBookmark(chatRoomId, chatId)
}

/** 대화 읽음 처리. */
class MarkChatAsReadUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String, lastChatId: String) =
        repository.markChatAsRead(chatRoomId, lastChatId)
}

/** 메시지별 안읽음 카운트 재요청. */
class RefreshChatUnreadCountsUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String) =
        repository.refreshChatUnreadCounts(chatRoomId)
}

/** 특정 대화방의 대화 리스트 Flow 구독 */
class GetChatsUseCase(private val repository: ChatRoomRepository) {
    operator fun invoke(chatRoomId: String): Flow<List<Chat.Item>> =
        repository.getChats(chatRoomId)
}

/** 대화 가져오기. */
class FetchChatsUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String) =
        repository.fetchChats(chatRoomId)
}

/** 위로 스크롤 도달 시 — Local DB 에서 더 오래된 대화 한 페이지 prepend */
class FetchMoreChatsUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String): Boolean =
        repository.fetchMoreChats(chatRoomId)
}

/** 아래로 스크롤 / 진입 직후 — 더 최신 대화 한 페이지 append. */
class FetchNewerChatsUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String): List<com.eunilsung.talk.domain.model.Chat.Item> =
        repository.fetchNewerChats(chatRoomId)
}

/** 대화방 참여자 리스트 조회 — 활성 참여자만 반환. */
class FetchChatRoomUsersUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String): List<com.eunilsung.talk.domain.model.User> =
        repository.fetchChatRoomUsers(chatRoomId)
}

/** 검색 조건(text + user + date)을 만족하는 chat 의 (id, date) 리스트. */
class SearchChatsUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(
        chatRoomId: String,
        query: String,
        userId: String,
        dateFrom: String,
        dateTo: String
    ): List<Pair<String, String>> =
        repository.searchChats(chatRoomId, query, userId, dateFrom, dateTo)
}

/** 검색 결과 chat + ±contextSize 컨텍스트를 메모리로 로드. */
class LoadChatWithContextUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(
        chatRoomId: String,
        chatId: String,
        contextSize: Int = 10
    ): Boolean = repository.loadChatWithContext(chatRoomId, chatId, contextSize)
}

/** 텍스트 대화 전송. */
class SendTextChatUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(
        chatRoomId: String,
        text: String,
        replyTarget: Chat.Item? = null,
        emoticonId: String? = null
    ) = repository.sendTextChat(chatRoomId, text, replyTarget, emoticonId)
}

/** 실패한 대화 재전송. */
class ResendFailedChatUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String, chatId: String) =
        repository.resendFailedChat(chatRoomId, chatId)
}

/** 실패한 대화 삭제. */
class DeleteFailedChatUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String, chatId: String) =
        repository.deleteFailedChat(chatRoomId, chatId)
}

/** 공감 토글 전송. */
class SendEmpathyUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String, targetChatId: String, empathyType: String) =
        repository.sendEmpathy(chatRoomId, targetChatId, empathyType)
}

/** 대화 회수. */
class RecallChatUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String, targetChatId: String) =
        repository.recallChat(chatRoomId, targetChatId)
}

/** "최신 대화로 이동" — 메모리를 DB 최신 한 페이지로 교체. */
class LoadLatestChatsUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String) =
        repository.loadLatestChats(chatRoomId)
}

/** 단일 파일 송신 (사진 / 동영상 / 파일). */
class SendFileUseCase(private val repository: ChatRoomRepository) {
    suspend operator fun invoke(chatRoomId: String, path: String) =
        repository.sendFile(chatRoomId, path)
}

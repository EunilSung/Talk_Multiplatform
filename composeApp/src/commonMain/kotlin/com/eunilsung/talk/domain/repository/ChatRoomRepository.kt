package com.eunilsung.talk.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.User

/** 대화(채팅) 도메인 Repository. */
interface ChatRoomRepository {

    /** 특정 대화방의 대화 리스트 Flow (날짜 오름차순). */
    fun getChats(chatRoomId: String): Flow<List<Chat.Item>>

    /** 대화 가져오기 — 마지막 대화 이후만, 없으면 처음부터. */
    suspend fun fetchChats(chatRoomId: String)

    /** 위로 스크롤 시 더 오래된 대화 한 페이지 추가 로드. 1건 이상이면 true. */
    suspend fun fetchMoreChats(chatRoomId: String): Boolean

    /**
     * 아래로 스크롤 시 Local DB 에서 더 최신 대화 한 페이지 추가 로드 (date ASC).
     * 진입 직후 호출 결과의 [List.firstOrNull] 은 첫 안읽음 대화(읽음 마커 위치).
     */
    suspend fun fetchNewerChats(chatRoomId: String): List<com.eunilsung.talk.domain.model.Chat.Item>

    /** 대화방 활성 참여자(User) 리스트 조회. 실패/빈 응답이면 emptyList. */
    suspend fun fetchChatRoomUsers(chatRoomId: String): List<User>

    /** Local DB 에서 검색 조건(본문/송신자/기간)을 만족하는 (chatId, date) 리스트, date DESC, 최대 500건. */
    suspend fun searchChats(
        chatRoomId: String,
        query: String,
        userId: String,
        dateFrom: String,
        dateTo: String
    ): List<Pair<String, String>>

    /** 특정 chatID 와 주변 컨텍스트(±contextSize)를 로드해 메모리 캐시에 머지. 성공/보유 시 true. */
    suspend fun loadChatWithContext(
        chatRoomId: String,
        chatId: String,
        contextSize: Int = 10
    ): Boolean

    /** 실시간 대화 수신 시 emit — value 는 도착한 chatRoomId. */
    val newChatPush: SharedFlow<String>

    /** 본인 전송 직후 emit — value 는 chatRoomId (무조건 최하단 스크롤용). */
    val mySendPush: SharedFlow<String>

    /** 현재 활성 방 캐시 해제 — 현재 활성 방이 chatRoomId 일 때만. */
    fun clearCurrentRoom(chatRoomId: String)

    /** 현재 활성 방의 공지. null 이면 공지 없음. */
    val currentNotice: kotlinx.coroutines.flow.StateFlow<com.eunilsung.talk.domain.model.Notice?>

    /** 대화방 공지 조회 — 결과가 [currentNotice] 에 실린다. */
    suspend fun requestNotice(chatRoomId: String)

    /**
     * 대화 하나를 [languageCode] 의 언어로 번역해 받는다. 지금 번역할 수 없으면 null.
     * 결과는 요청한 사람만 보고 방에는 남지 않는다.
     */
    suspend fun translateChat(chatRoomId: String, chatId: String, languageCode: String): String?

    /** 보내려고 쓴 글을 [style] 대로 다듬어 받는다. 지금 다듬을 수 없으면 null. 어디에도 남지 않는다. */
    suspend fun polishText(text: String, style: com.eunilsung.talk.domain.model.PolishStyle): String?

    /** 공지 등록 — [currentNotice] 갱신 + 시스템 대화 추가. */
    suspend fun addNotice(chatRoomId: String, content: String)

    /** 공지 삭제 — [currentNotice]=null + 시스템 대화 추가. */
    suspend fun deleteNotice(chatRoomId: String)

    /** 현재 활성 방의 책갈피 리스트. */
    val bookmarks: kotlinx.coroutines.flow.StateFlow<List<com.eunilsung.talk.domain.model.Bookmark>>

    /** 대화방 책갈피 리스트 조회 — 결과가 [bookmarks] 에 실린다. */
    suspend fun fetchBookmarks(chatRoomId: String)

    /** 책갈피 등록 — [bookmarks] 에 추가. */
    suspend fun addBookmark(chatRoomId: String, chat: com.eunilsung.talk.domain.model.Chat.Item)

    /** 책갈피 해제 — [bookmarks] 에서 해당 chatId 제거. */
    suspend fun deleteBookmark(chatRoomId: String, chatId: String)

    /**
     * 텍스트 대화 전송 — 즉시 목록에 선반영.
     * @param replyTarget 답장 인용 원본. null = 일반 텍스트.
     */
    suspend fun sendTextChat(
        chatRoomId: String,
        text: String,
        replyTarget: Chat.Item? = null,
        emoticonId: String? = null,
    )

    /** 실패(FAIL) 대화 재전송 — 기존 FAIL 행 제거 후 새 chatID 로 송신. FAIL 아니면 no-op. */
    suspend fun resendFailedChat(chatRoomId: String, chatId: String)

    /** 실패(FAIL) 대화 삭제 — 메모리/DB 에서 즉시 제거. FAIL 아니면 no-op. */
    suspend fun deleteFailedChat(chatRoomId: String, chatId: String)

    /**
     * 공감 전송 — 토글: 같은 type 있으면 제거, 다른 type 있으면 갱신, 없으면 추가.
     * @param empathyType "0".."5" 중 하나.
     */
    suspend fun sendEmpathy(
        chatRoomId: String,
        targetChatId: String,
        empathyType: String
    )

    /** 대화 회수 — 대상의 isRecalled 를 세워 "회수된 메시지" 로 표시. */
    suspend fun recallChat(chatRoomId: String, targetChatId: String)

    /** 파일/사진/동영상 대화 전송 — metadata 해석 후 chatType 판별해 목록에 추가. */
    suspend fun sendFile(chatRoomId: String, path: String)

    /** "최신 대화로 이동" — 메모리를 DB 최신 한 페이지로 교체. 완료 후 [latestLoadedPush] emit. */
    suspend fun loadLatestChats(chatRoomId: String)

    /** [loadLatestChats] 완료 직후 emit — value 는 대상 chatRoomId. */
    val latestLoadedPush: SharedFlow<String>

    /** 대화방 참여자 변경(타인 초대/퇴장) 시 emit — value 는 chatRoomId. */
    val roomUsersChanged: SharedFlow<String>

    /** 본인이 대화방에서 퇴장 처리될 때 emit — value 는 떠난 chatRoomId. */
    val selfLeftPush: SharedFlow<String>

    /** 대화 읽음 처리. 성공 여부 반환. */
    suspend fun markChatAsRead(chatRoomId: String, lastChatId: String): Boolean

    /** 메시지별 안읽음 카운트 재조회 — 메모리 chats 의 [Chat.Item.unReadCount] 갱신. */
    suspend fun refreshChatUnreadCounts(chatRoomId: String)
}

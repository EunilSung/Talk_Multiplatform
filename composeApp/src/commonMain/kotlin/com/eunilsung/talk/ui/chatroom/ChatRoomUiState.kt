package com.eunilsung.talk.ui.chatroom

import androidx.compose.runtime.Immutable
import com.eunilsung.talk.data.local.PhotoAlbum
import com.eunilsung.talk.data.local.RecentPhoto
import com.eunilsung.talk.data.local.RecentPhotosResult
import com.eunilsung.talk.domain.model.Bookmark
import com.eunilsung.talk.domain.model.Chat
import com.eunilsung.talk.domain.model.ChatRoom
import com.eunilsung.talk.domain.model.Notice
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.model.ChatSettingsState

/** 대화방 화면의 상태. */
data class ChatRoomUiState(
    /** 상단바에 그릴 방. 대화방 목록에 아직 없으면 null. */
    val chatRoom: ChatRoom.Item? = null,
    val messages: ChatMessagesUiState = ChatMessagesUiState.Loading,
    val users: List<User> = emptyList(),
    /** 이 방의 전송 주체 — `null` 이면 나. 상단바 아이콘 강조와 선택 목록 표시에 쓴다. */
    val sender: User? = null,
    /** 로그인한 나의 아이디. 참여자 목록의 '나' 표시와 전송 주체 기본값에 쓴다. */
    val myId: String = "",
    val search: ChatSearchUiState = ChatSearchUiState(),
    val replyTarget: Chat.Item? = null,
    /** 지금 걸린 공지. 없으면 null. */
    val notice: Notice? = null,
    val noticeBar: NoticeBarUiState = NoticeBarUiState(),
    val bookmarks: List<Bookmark> = emptyList(),
    /** 대화 id 별 번역. 이 화면에서만 들고 있는다. */
    val translations: Map<String, ChatTranslationUiState> = emptyMap(),
    /** 보내기 전의 글 다듬기 상태. */
    val polish: PolishUiState = PolishUiState.Idle,
    val chatSettings: ChatSettingsState = ChatSettingsState(),
    /**
     * 이모티콘 패널을 처음 열 때 고를 탭 — 마지막으로 고른 탭.
     *
     * VM 은 대화방 화면들이 함께 쓰므로 방을 열 때마다([ChatRoomActions.Load]) 다시 읽는다.
     */
    val initialEmoticonTab: Int = 0,
    val media: ChatMediaUiState = ChatMediaUiState(),
)

/** 대화 찾기의 상태 — 검색어·사람·날짜 조건과 찾은 대화 사이의 현재 위치. */
data class ChatSearchUiState(
    val isActive: Boolean = false,
    val query: String = "",
    val committedQuery: String = "",
    val searchedUser: User? = null,
    val searchedDate: String? = null,
    val matchedChatIds: List<String> = emptyList(),
    val currentIndex: Int = -1,
    val focusChatId: String? = null,
    val focusTriggerKey: Int = 0
) {
    val currentMatchChatId: String? get() = matchedChatIds.getOrNull(currentIndex)

    val totalMatches: Int get() = matchedChatIds.size

    val currentPosition: Int get() = if (currentIndex < 0) 0 else currentIndex + 1
}

/** 대화 목록의 상태. */
sealed interface ChatMessagesUiState {
    data object Loading : ChatMessagesUiState
    /** kotlin List 는 unstable 로 추론된다 — 내용이 불변임을 명시해 화면 단위 skip 을 살린다. */
    @Immutable
    data class Success(val groupedChats: List<GroupedChat>) : ChatMessagesUiState
    data class Error(val message: String) : ChatMessagesUiState
}

/** 사진·파일 보내기 시트의 상태. */
data class ChatMediaUiState(
    val recentPhotos: RecentPhotosResult = RecentPhotosResult.Granted(emptyList()),
    val galleryPhotos: List<RecentPhoto> = emptyList(),
    /** 갤러리 시트 상단 필터에 뿌릴 앨범 목록. */
    val galleryAlbums: List<PhotoAlbum> = emptyList(),
    /** 선택된 앨범. null = 전체. */
    val selectedAlbumId: String? = null,
    /** 사진첩 전체를 볼 수 있는지. 시트를 열 때마다 다시 확인한다. */
    val hasPhotoAccess: Boolean = false,
)

/** 공지바의 접힘·상세표시·영구숨김 상태. 공지마다 기기에 기억해 둔다. */
data class NoticeBarUiState(
    val hidden: Boolean = false,
    val expanded: Boolean = true,
    val showDetails: Boolean = true,
)

/** 말풍선 아래에 붙는 번역의 상태. 번역은 요청한 사람의 화면에만 있고 화면을 나가면 사라진다. */
sealed interface ChatTranslationUiState {
    data object Loading : ChatTranslationUiState
    data class Ready(val text: String) : ChatTranslationUiState
}

/** 보내기 전의 글 다듬기 상태. */
sealed interface PolishUiState {
    data object Idle : PolishUiState
    data object Loading : PolishUiState

    /** [sourceText] 를 다듬은 결과. 그 사이 입력창이 바뀌었으면 화면이 덮어쓰지 않는다. */
    data class Ready(val sourceText: String, val text: String) : PolishUiState
    data object Failed : PolishUiState
}

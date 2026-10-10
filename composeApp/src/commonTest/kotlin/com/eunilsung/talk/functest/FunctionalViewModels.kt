package com.eunilsung.talk.functest

import com.eunilsung.talk.data.local.FilePickerProvider
import com.eunilsung.talk.data.local.NoticeUiStateStore
import com.eunilsung.talk.data.local.PickedFile
import com.eunilsung.talk.data.local.RecentPhotosProvider
import com.eunilsung.talk.data.local.RecentPhotosResult
import com.eunilsung.talk.data.repository.ChatSettingsRepositoryImpl
import com.eunilsung.talk.data.sample.ChatSenderOverride
import com.eunilsung.talk.domain.usecase.*
import com.eunilsung.talk.ui.chatroom.ChatRoomViewModel
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 진짜 서버에 붙은 저장소 위에 대화방 ViewModel 을 세운다.
 *
 * 화면이 보는 상태는 ViewModel 이 만든다 — 안읽음 표시선이 어디에 서는지, 번역이 어느 말풍선에
 * 붙는지, 다듬은 글이 언제 준비되는지. 저장소만 봐서는 이 판단들이 맞는지 알 수 없다.
 * 서버와 무관한 기기 기능(사진첩, 파일 고르기)만 빈 대역으로 채운다.
 */
fun RealServerHarness.chatRoomViewModel(): ChatRoomViewModel {
    val chatUseCases = ChatRoomUseCases(
        getChats = GetChatsUseCase(chats),
        fetchChats = FetchChatsUseCase(chats),
        fetchMoreChats = FetchMoreChatsUseCase(chats),
        fetchNewerChats = FetchNewerChatsUseCase(chats),
        fetchChatRoomUsers = FetchChatRoomUsersUseCase(chats),
        searchChats = SearchChatsUseCase(chats),
        loadChatWithContext = LoadChatWithContextUseCase(chats),
        sendTextChat = SendTextChatUseCase(chats),
        resendFailedChat = ResendFailedChatUseCase(chats),
        deleteFailedChat = DeleteFailedChatUseCase(chats),
        sendEmpathy = SendEmpathyUseCase(chats),
        recallChat = RecallChatUseCase(chats),
        loadLatestChats = LoadLatestChatsUseCase(chats),
        sendFile = SendFileUseCase(chats),
        markChatAsRead = MarkChatAsReadUseCase(chats),
        refreshChatUnreadCounts = RefreshChatUnreadCountsUseCase(chats),
        addNotice = AddNoticeUseCase(chats),
        deleteNotice = DeleteNoticeUseCase(chats),
        requestNotice = RequestNoticeUseCase(chats),
        polishText = PolishTextUseCase(chats),
        translateChat = TranslateChatUseCase(chats),
        fetchBookmarks = FetchBookmarksUseCase(chats),
        addBookmark = AddBookmarkUseCase(chats),
        deleteBookmark = DeleteBookmarkUseCase(chats),
    )
    val roomUseCases = ChatRoomListUseCases(
        getChatRooms = GetChatRoomsUseCase(rooms),
        fetchChatRooms = FetchChatRoomsUseCase(rooms),
        renameChatRoom = RenameChatRoomUseCase(rooms),
        setChatRoomAlarm = SetChatRoomAlarmUseCase(rooms),
        setChatRoomPin = SetChatRoomPinUseCase(rooms),
        leaveChatRoom = LeaveChatRoomUseCase(rooms),
        observeNewChatRoomPush = ObserveNewChatRoomPushUseCase(rooms),
        observeChatRoomUnreadTotal = ObserveChatRoomUnreadTotalUseCase(rooms),
        observeIsFetching = ObserveChatRoomFetchingUseCase(rooms),
        observeChatGroups = ObserveChatGroupsUseCase(rooms),
        addRoomToGroup = AddRoomToGroupUseCase(rooms),
        removeRoomFromGroup = RemoveRoomFromGroupUseCase(rooms),
        createChatGroup = CreateChatGroupUseCase(rooms),
        renameChatGroup = RenameChatGroupUseCase(rooms),
        deleteChatGroup = DeleteChatGroupUseCase(rooms),
        reorderChatGroups = ReorderChatGroupsUseCase(rooms),
    )
    return ChatRoomViewModel(
        chatRoomUseCases = chatUseCases,
        chatRoomRepository = chats,
        recentPhotosProvider = NoPhotos,
        filePickerProvider = NoFilePicker,
        chatSettingsRepository = ChatSettingsRepositoryImpl(MapSettings()),
        chatRoomListUseCases = roomUseCases,
        inviteUseCases = InviteUseCases(InviteUsersUseCase(invite)),
        senderOverride = ChatSenderOverride(),
        noticeUiStateStore = NoticeUiStateStore(MapSettings()),
    )
}

/** 사진첩이 없는 기기. */
private object NoPhotos : RecentPhotosProvider {
    override suspend fun fetchRecent(limit: Int, onPartial: (RecentPhotosResult) -> Unit): RecentPhotosResult =
        RecentPhotosResult.Denied

    override fun hasPhotoAccess(): Boolean = false
    override fun openAppSettings() = Unit
    override fun requestPhotoAccess() = Unit
}

/** 파일을 고를 수 없는 기기. */
private object NoFilePicker : FilePickerProvider {
    override val results: SharedFlow<List<PickedFile>> = MutableSharedFlow()
    override val lastRequestId: String = ""
    override fun launchFilePicker(requestId: String, allowMultiple: Boolean, mimeTypes: List<String>) = Unit
}

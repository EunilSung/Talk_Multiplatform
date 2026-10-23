package com.eunilsung.talk.functest

import com.eunilsung.talk.data.local.FilePickerProvider
import com.eunilsung.talk.data.local.NoticeUiStateStore
import com.eunilsung.talk.data.local.PickedFile
import com.eunilsung.talk.data.local.RecentPhotosProvider
import com.eunilsung.talk.data.local.RecentPhotosResult
import com.eunilsung.talk.data.repository.ChatSettingsRepositoryImpl
import com.eunilsung.talk.data.local.repository.ChatSenderOverride
import com.eunilsung.talk.data.local.repository.LocalVersionCheckRepositoryImpl
import com.eunilsung.talk.domain.model.PushPayload
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.domain.repository.PushNotifier
import com.eunilsung.talk.domain.usecase.*
import com.eunilsung.talk.ui.chatroom.ChatRoomViewModel
import com.eunilsung.talk.ui.chatroom.vote.VoteViewModel
import com.eunilsung.talk.ui.chatroomlist.ChatRoomListViewModel
import com.eunilsung.talk.ui.chatroomlist.group.ChatRoomGroupManageViewModel
import com.eunilsung.talk.ui.group.GroupViewModel
import com.eunilsung.talk.ui.invite.InviteViewModel
import com.eunilsung.talk.ui.invite.tab.group.InviteGroupViewModel
import com.eunilsung.talk.ui.login.LoginViewModel
import com.eunilsung.talk.ui.userprofile.UserProfileViewModel
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import com.eunilsung.talk.data.remote.push.CurrentChatRoomTracker

/**
 * ViewModel 이 도는 메인 스레드의 대역. 진짜 메인 스레드처럼 한 번에 하나씩만 돌린다.
 *
 * 여러 스레드에서 돌리면 기기에서는 일어나지 않는 끼어들기가 생긴다 — 목록 갱신이 읽어 둔 옛 상태로 방금 바뀐
 * 상태를 덮는다.
 */
val viewModelMainThread: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

/**
 * 진짜 서버에 붙은 저장소 위에 대화방 ViewModel 을 세운다.
 *
 * 화면이 보는 상태는 ViewModel 이 만든다 — 안읽음 표시선이 어디에 서는지, 번역이 어느 말풍선에
 * 붙는지, 다듬은 글이 언제 준비되는지. 저장소만 봐서는 이 판단들이 맞는지 알 수 없다.
 * 서버와 무관한 기기 기능(사진첩, 파일 고르기, 다른 앱 열기)만 대역으로 채운다.
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
        reinviteDirectPartner = ReinviteDirectPartnerUseCase(rooms, invite),
        requestAiSummary = RequestAiSummaryUseCase(aiSummary, login),
    )
    val roomUseCases = chatRoomListUseCases()
    val chatSettings = ChatSettingsRepositoryImpl(MapSettings())
    val senders = ChatSenderOverride()
    val noticeBar = NoticeUiStateStore(MapSettings())
    val stateUseCases = ChatRoomStateUseCases(
        observeNewChat = ObserveNewChatUseCase(chats),
        observeMySend = ObserveMySendUseCase(chats),
        observeLatestLoaded = ObserveLatestLoadedUseCase(chats),
        observeSelfLeft = ObserveSelfLeftUseCase(chats),
        observeRoomUsersChanged = ObserveRoomUsersChangedUseCase(chats),
        observeCurrentNotice = ObserveCurrentNoticeUseCase(chats),
        observeBookmarks = ObserveBookmarksUseCase(chats),
        clearCurrentRoom = ClearCurrentRoomUseCase(chats),
        isCurrentChatRoom = IsCurrentChatRoomUseCase(CurrentChatRoomTracker),
        observeChatSettings = ObserveChatSettingsUseCase(chatSettings),
        getLastEmoticonTab = GetLastEmoticonTabUseCase(chatSettings),
        setLastEmoticonTab = SetLastEmoticonTabUseCase(chatSettings),
        getSender = GetSenderUseCase(senders),
        selectSender = SelectSenderUseCase(senders),
        getNoticeBarState = GetNoticeBarStateUseCase(noticeBar),
        setNoticeCollapsed = SetNoticeCollapsedUseCase(noticeBar),
        setNoticeDetailsShown = SetNoticeDetailsShownUseCase(noticeBar),
        hideNotice = HideNoticeUseCase(noticeBar),
        openChatRoom = OpenChatRoomUseCase(CurrentChatRoomTracker, NoNotifications),
        closeChatRoom = CloseChatRoomUseCase(CurrentChatRoomTracker, NoNotifications, chats),
        cancelChatRoomNotification = CancelChatRoomNotificationUseCase(NoNotifications),
        getMyUser = GetMyUserUseCase(login),
    )
    return own(
        ChatRoomViewModel(
            chatRoomUseCases = chatUseCases,
            stateUseCases = stateUseCases,
            recentPhotosProvider = NoPhotos,
            filePickerProvider = NoFilePicker,
            chatRoomListUseCases = roomUseCases,
        )
    )
}

/** 대화함 화면. 방 목록·검색·필터 칩·편집 모드의 상태를 만든다. */
fun RealServerHarness.chatRoomListViewModel(): ChatRoomListViewModel =
    own(ChatRoomListViewModel(chatRoomListUseCases()))

/** 대화그룹 관리 화면. */
fun RealServerHarness.chatRoomGroupManageViewModel(): ChatRoomGroupManageViewModel =
    own(ChatRoomGroupManageViewModel(chatRoomListUseCases()))

/** 로그인 화면. [repository] 는 이 도구의 로그인과 따로 도는 기기다([RealServerHarness.otherDeviceLogin]). */
fun RealServerHarness.loginViewModel(repository: LoginRepository): LoginViewModel {
    val useCases = LoginUseCases(
        login = LoginUseCase(repository),
        logout = LogoutUseCase(repository),
        duplicateLogin = DuplicateLoginUseCase(repository),
        observeLoginState = ObserveLoginStateUseCase(repository),
        getSavedLogin = GetSavedLoginUseCase(repository),
        setSavePassword = SetSavePasswordUseCase(repository),
        getMyUser = GetMyUserUseCase(repository),
    )
    return own(LoginViewModel(useCases, VersionCheckUseCase(LocalVersionCheckRepositoryImpl())))
}

/** 투표 화면. 목록·만들기·참여·결과를 오간다. */
fun RealServerHarness.voteViewModel(): VoteViewModel {
    val useCases = VoteUseCases(
        getVotes = GetVotesUseCase(votes),
        getVote = GetVoteUseCase(votes),
        createVote = CreateVoteUseCase(votes),
        submitVote = SubmitVoteUseCase(votes),
        reVote = ReVoteUseCase(votes),
        closeVote = CloseVoteUseCase(votes),
        getMyUser = GetMyUserUseCase(login),
    )
    return own(VoteViewModel(useCases))
}

/** 초대 화면. 사람을 골라 새 방을 만들거나 있던 방에 부른다. */
fun RealServerHarness.inviteViewModel(): InviteViewModel = own(InviteViewModel(inviteUseCases()))

/** 초대 화면의 그룹 탭. */
fun RealServerHarness.inviteGroupViewModel(): InviteGroupViewModel = own(InviteGroupViewModel(groupUseCases()))

/** 내 그룹 화면. */
fun RealServerHarness.groupViewModel(): GroupViewModel = own(GroupViewModel(groupUseCases(), inviteUseCases()))

/** 프로필 화면. */
fun RealServerHarness.userProfileViewModel(): UserProfileViewModel {
    val useCases = UserProfileUseCases(
        getCachedProfile = GetCachedProfileUseCase(profiles),
        fetchProfile = FetchProfileUseCase(profiles),
        getMyUser = GetMyUserUseCase(login),
    )
    return own(UserProfileViewModel(useCases))
}

private fun RealServerHarness.chatRoomListUseCases() = ChatRoomListUseCases(
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

private fun RealServerHarness.inviteUseCases(): InviteUseCases {
    val createChatRoom = CreateChatRoomUseCase(invite, rooms, login)
    return InviteUseCases(
        inviteUsers = InviteUsersUseCase(invite),
        createChatRoom = createChatRoom,
        startDirectChat = StartDirectChatUseCase(createChatRoom, profiles),
        getMyUser = GetMyUserUseCase(login),
    )
}

private fun RealServerHarness.groupUseCases() = GroupUseCases(
    getGroups = GetGroupsUseCase(groups),
    fetchGroups = FetchGroupsUseCase(groups),
    toggleGroupExpanded = ToggleGroupExpandedUseCase(groups),
    removeUserFromGroup = RemoveUserFromGroupUseCase(groups),
    moveUserToGroup = MoveUserToGroupUseCase(groups),
    renameGroup = RenameGroupUseCase(groups),
    deleteGroup = DeleteGroupUseCase(groups),
    createGroup = CreateGroupUseCase(groups),
    copyUserToGroup = CopyUserToGroupUseCase(groups),
)

/** 사진첩이 없는 기기. */
private object NoPhotos : RecentPhotosProvider {
    override suspend fun fetchRecent(limit: Int, onPartial: (RecentPhotosResult) -> Unit): RecentPhotosResult =
        RecentPhotosResult.Denied

    override fun hasPhotoAccess(): Boolean = false
    override fun openAppSettings() = Unit
    override fun requestPhotoAccess() = Unit
}

/** 알림을 띄우지 않는 기기. */
private object NoNotifications : PushNotifier {
    override fun show(payload: PushPayload) = Unit
    override fun cancel(key: String) = Unit
    override fun setBadge(count: Int) = Unit
}

/** 파일을 고를 수 없는 기기. */
private object NoFilePicker : FilePickerProvider {
    override val results: SharedFlow<List<PickedFile>> = MutableSharedFlow()
    override val lastRequestId: String = ""
    override fun launchFilePicker(requestId: String, allowMultiple: Boolean, mimeTypes: List<String>) = Unit
}

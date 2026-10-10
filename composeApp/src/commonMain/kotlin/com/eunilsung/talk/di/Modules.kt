package com.eunilsung.talk.di

import com.russhwolf.settings.Settings
import com.eunilsung.talk.Config
import com.eunilsung.talk.data.remote.server.AuthTokenStore
import com.eunilsung.talk.data.remote.server.TalkServer
import com.eunilsung.talk.data.remote.server.TalkServerClient
import com.eunilsung.talk.data.mapper.ServerChatMapper
import com.eunilsung.talk.data.remote.server.ServerEvents
import com.eunilsung.talk.data.remote.server.ServerFileStore
import com.eunilsung.talk.data.remote.server.TalkSocket
import com.eunilsung.talk.data.repository.ChatRoomListRepositoryImpl
import com.eunilsung.talk.data.repository.ChatRoomRepositoryImpl
import com.eunilsung.talk.data.repository.InviteRepositoryImpl
import com.eunilsung.talk.data.remote.server.UserDirectory
import com.eunilsung.talk.data.repository.GroupRepositoryImpl
import com.eunilsung.talk.data.repository.GroupRepositoryImpl.Companion.toGroupUser
import com.eunilsung.talk.data.repository.LoginRepositoryImpl
import com.eunilsung.talk.data.repository.PushTokenRepositoryImpl
import com.eunilsung.talk.data.repository.UserProfileRepositoryImpl
import com.eunilsung.talk.data.repository.VoteRepositoryImpl
import com.eunilsung.talk.data.local.DatabaseDriverFactory
import com.eunilsung.talk.data.local.NoticeUiStateStore
import com.eunilsung.talk.data.mapper.ChatMapper
import com.eunilsung.talk.data.mapper.ChatRoomMapper
import com.eunilsung.talk.db.AppDatabase
import com.eunilsung.talk.data.mapper.GroupMapper
import com.eunilsung.talk.data.repository.ChatSettingsRepositoryImpl
import com.eunilsung.talk.data.sample.LocalChatRoomListRepositoryImpl
import com.eunilsung.talk.data.sample.ChatSenderOverride
import com.eunilsung.talk.domain.repository.SenderOverrideRepository
import com.eunilsung.talk.data.sample.LocalChatRoomRepositoryImpl
import com.eunilsung.talk.data.sample.LocalGroupRepositoryImpl
import com.eunilsung.talk.data.sample.LocalInviteRepositoryImpl
import com.eunilsung.talk.data.sample.LocalLoginRepositoryImpl
import com.eunilsung.talk.data.sample.LocalMainRepositoryImpl
import com.eunilsung.talk.data.sample.LocalNotificationSettingsRepositoryImpl
import com.eunilsung.talk.data.sample.LocalPushTokenRepositoryImpl
import com.eunilsung.talk.data.sample.LocalUserProfileRepositoryImpl
import com.eunilsung.talk.data.sample.LocalVoteRepositoryImpl
import com.eunilsung.talk.data.sample.LocalVersionCheckRepositoryImpl
import com.eunilsung.talk.ui.screenlock.ScreenLockViewModel
import com.eunilsung.talk.domain.repository.ChatRoomRepository
import com.eunilsung.talk.domain.repository.ChatRoomListRepository
import com.eunilsung.talk.domain.repository.InviteRepository
import com.eunilsung.talk.domain.repository.VersionCheckRepository
import com.eunilsung.talk.domain.repository.ChatSettingsRepository
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.domain.repository.NotificationSettingsRepository
import com.eunilsung.talk.domain.repository.PushTokenRepository
import com.eunilsung.talk.domain.repository.MainRepository
import com.eunilsung.talk.domain.repository.VoteRepository
import com.eunilsung.talk.domain.repository.GroupRepository
import com.eunilsung.talk.domain.repository.UserProfileRepository
import com.eunilsung.talk.domain.usecase.*
import com.eunilsung.talk.ui.chatroom.ChatRoomViewModel
import com.eunilsung.talk.ui.chatroomlist.group.ChatRoomRoomGroupManageViewModel
import com.eunilsung.talk.ui.invite.InviteViewModel
import com.eunilsung.talk.ui.invite.tab.group.InviteGroupViewModel
import com.eunilsung.talk.ui.share.ShareViewModel
import com.eunilsung.talk.ui.login.LoginViewModel
import com.eunilsung.talk.ui.main.MainViewModel
import com.eunilsung.talk.ui.group.GroupViewModel
import com.eunilsung.talk.ui.userprofile.UserProfileViewModel
import com.eunilsung.talk.ui.chatroomlist.ChatRoomListViewModel
import com.eunilsung.talk.ui.setting.SettingViewModel
import com.eunilsung.talk.ui.chatroom.vote.VoteViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { Settings() }

    single<AppDatabase> { 
        val factory: DatabaseDriverFactory = get()
        AppDatabase(factory.createDriver())
    }
    /** 서버 주소가 있으면 FCM 토큰을 서버에 등록하고, 없으면 로그만 남긴다. */
    single<PushTokenRepository>(createdAtStart = true) {
        if (Config.Server.IS_ENABLED) PushTokenRepositoryImpl(get(), get(), get()) else LocalPushTokenRepositoryImpl()
    }
    single { com.eunilsung.talk.data.remote.push.PushPayloadParser() }

    single { com.eunilsung.talk.data.remote.linkpreview.LinkPreviewRepository(get()) }

    // 원격 XML 대신 즉시 "업데이트 불필요" 반환.
    single<VersionCheckRepository> { LocalVersionCheckRepositoryImpl() }
    singleOf(::VersionCheckUseCase)

    viewModelOf(::LoginViewModel)
    single { AuthTokenStore(get()) }
    single<TalkServer> { TalkServerClient(get(), get()) }
    single<ServerEvents> { TalkSocket(get(), get()) }
    singleOf(::ServerFileStore)
    singleOf(::ServerChatMapper)
    /** 서버 주소가 있으면 서버에, 없으면 TestAccounts(test1~test10 / 1234) 로 인증. */
    single<LoginRepository> {
        if (Config.Server.IS_ENABLED) LoginRepositoryImpl(get(), get(), get()) else LocalLoginRepositoryImpl(get())
    }
    singleOf(::LoginUseCase)
    singleOf(::DuplicateLoginUseCase)
    singleOf(::LogoutUseCase)
    singleOf(::LoginUseCases)

    viewModelOf(::MainViewModel)
    // 강제 로그아웃은 발생하지 않는다.
    single<MainRepository> { LocalMainRepositoryImpl() }

    viewModelOf(::GroupViewModel)
    // TestAccounts / TestGroups 로 그룹 구성.
    singleOf(::UserDirectory)
    single<GroupRepository> {
        if (Config.Server.IS_ENABLED) {
            val directory: UserDirectory = get()
            val local = LocalGroupRepositoryImpl(
                get(), get(), get(), get(),
                seedsSampleGroups = false,
                findUser = { userId, groupId -> directory.find(userId)?.toGroupUser(groupId) },
            )
            GroupRepositoryImpl(local, get(), directory, get())
        } else {
            LocalGroupRepositoryImpl(get(), get(), get(), get())
        }
    }
    singleOf(::GroupMapper)
    singleOf(::GetGroupsUseCase)
    singleOf(::FetchGroupsUseCase)
    singleOf(::ToggleGroupExpandedUseCase)
    singleOf(::RemoveUserFromGroupUseCase)
    singleOf(::MoveUserToGroupUseCase)
    singleOf(::RenameGroupUseCase)
    singleOf(::DeleteGroupUseCase)
    singleOf(::CreateGroupUseCase)
    singleOf(::GroupUseCases)

    viewModelOf(::InviteViewModel)
    viewModelOf(::InviteGroupViewModel)
    // ChatRoomEntity 에 직접 방을 만들고 참여자를 추가.
    single<InviteRepository> {
        if (Config.Server.IS_ENABLED) InviteRepositoryImpl(get(), get()) else LocalInviteRepositoryImpl(get(), get(), get())
    }
    singleOf(::InviteUsersUseCase)
    singleOf(::InviteUseCases)

    viewModelOf(::ShareViewModel)

    viewModelOf(::UserProfileViewModel)
    // TestAccounts 를 프로필로 사용.
    single<UserProfileRepository> {
        if (Config.Server.IS_ENABLED) UserProfileRepositoryImpl(get(), get()) else LocalUserProfileRepositoryImpl()
    }

    viewModelOf(::ChatRoomViewModel)
    singleOf(::NoticeUiStateStore)

    singleOf(::ChatMapper)
    // ChatEntity 에 대화를 직접 읽고 쓴다. (투표 저장소도 구현 타입이 필요해 둘 다 등록)
    // 전송 주체 전환(샘플 전용) — 싱글턴.
    single<SenderOverrideRepository> { ChatSenderOverride() }
    single {
        LocalChatRoomRepositoryImpl(
            get(), get(), get<LocalChatRoomListRepositoryImpl>(), get(), get(), get(),
            seedsSampleChats = !Config.Server.IS_ENABLED,
        )
    }
    /** 서버 주소가 있으면 서버와 주고받고, 로컬 구현은 그 밑에서 캐시와 미연동 기능을 맡는다. */
    single<ChatRoomRepository> {
        if (Config.Server.IS_ENABLED) ChatRoomRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get())
        else get<LocalChatRoomRepositoryImpl>()
    }
    singleOf(::GetChatsUseCase)
    singleOf(::FetchChatsUseCase)
    singleOf(::FetchMoreChatsUseCase)
    singleOf(::FetchNewerChatsUseCase)
    singleOf(::FetchChatRoomUsersUseCase)
    singleOf(::SearchChatsUseCase)
    singleOf(::LoadChatWithContextUseCase)
    singleOf(::SendTextChatUseCase)
    singleOf(::ResendFailedChatUseCase)
    singleOf(::DeleteFailedChatUseCase)
    singleOf(::SendEmpathyUseCase)
    singleOf(::RecallChatUseCase)
    singleOf(::LoadLatestChatsUseCase)
    singleOf(::SendFileUseCase)
    singleOf(::MarkChatAsReadUseCase)
    singleOf(::RefreshChatUnreadCountsUseCase)
    singleOf(::AddNoticeUseCase)
    singleOf(::DeleteNoticeUseCase)
    singleOf(::RequestNoticeUseCase)
    singleOf(::TranslateChatUseCase)
    singleOf(::FetchBookmarksUseCase)
    singleOf(::AddBookmarkUseCase)
    singleOf(::DeleteBookmarkUseCase)
    /** 인자가 22개를 넘어 `singleOf` 로는 등록할 수 없다. */
    single {
        ChatRoomUseCases(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }

    viewModelOf(::ChatRoomListViewModel)
    singleOf(::ChatRoomMapper)
    // TestChatRooms 로 대화방/그룹 칩 구성.
    single {
        LocalChatRoomListRepositoryImpl(get(), get(), get(), get(), seedsSampleRooms = !Config.Server.IS_ENABLED)
    }
    single<ChatRoomListRepository> {
        if (Config.Server.IS_ENABLED) ChatRoomListRepositoryImpl(get(), get(), get(), get(), get())
        else get<LocalChatRoomListRepositoryImpl>()
    }
    singleOf(::GetChatRoomsUseCase)
    singleOf(::FetchChatRoomsUseCase)
    singleOf(::RenameChatRoomUseCase)
    singleOf(::SetChatRoomAlarmUseCase)
    singleOf(::SetChatRoomPinUseCase)
    singleOf(::LeaveChatRoomUseCase)
    singleOf(::ObserveNewChatRoomPushUseCase)
    singleOf(::ObserveChatRoomUnreadTotalUseCase)
    singleOf(::ObserveChatRoomFetchingUseCase)
    singleOf(::ObserveChatGroupsUseCase)
    singleOf(::AddRoomToGroupUseCase)
    singleOf(::RemoveRoomFromGroupUseCase)
    singleOf(::CreateChatGroupUseCase)
    singleOf(::RenameChatGroupUseCase)
    singleOf(::DeleteChatGroupUseCase)
    singleOf(::ReorderChatGroupsUseCase)
    singleOf(::ChatRoomListUseCases)
    viewModelOf(::ChatRoomRoomGroupManageViewModel)

    // 알림 설정은 로컬 저장만 한다.
    single<NotificationSettingsRepository> { LocalNotificationSettingsRepositoryImpl(get(), get()) }
    single<ChatSettingsRepository> { ChatSettingsRepositoryImpl(get()) }
    single<com.eunilsung.talk.domain.repository.ScreenLockRepository> {
        com.eunilsung.talk.data.repository.ScreenLockRepositoryImpl(get())
    }
    viewModelOf(::ScreenLockViewModel)
    viewModelOf(::SettingViewModel)

    viewModelOf(::VoteViewModel)
    // 투표/투표 결과는 VoteEntity 에 저장.
    single<VoteRepository> {
        if (Config.Server.IS_ENABLED) VoteRepositoryImpl(get(), get(), get()) else LocalVoteRepositoryImpl(get(), get())
    }
    singleOf(::GetVotesUseCase)
    singleOf(::GetVoteUseCase)
    singleOf(::CreateVoteUseCase)
    singleOf(::SubmitVoteUseCase)
    singleOf(::ReVoteUseCase)
    singleOf(::CloseVoteUseCase)
    singleOf(::VoteUseCases)
}

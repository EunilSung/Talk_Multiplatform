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
import com.eunilsung.talk.data.mapper.SeedMapper
import com.eunilsung.talk.data.mapper.ServerPeopleMapper
import com.eunilsung.talk.data.mapper.VoteMapper
import com.eunilsung.talk.data.repository.ChatSettingsRepositoryImpl
import com.eunilsung.talk.data.local.repository.LocalChatRoomListRepositoryImpl
import com.eunilsung.talk.data.local.repository.ChatSenderOverride
import com.eunilsung.talk.domain.repository.SenderOverrideRepository
import com.eunilsung.talk.data.local.repository.LocalChatRoomRepositoryImpl
import com.eunilsung.talk.data.local.repository.LocalGroupRepositoryImpl
import com.eunilsung.talk.data.local.repository.LocalInviteRepositoryImpl
import com.eunilsung.talk.data.local.repository.LocalLoginRepositoryImpl
import com.eunilsung.talk.data.local.repository.LocalMainRepositoryImpl
import com.eunilsung.talk.data.local.repository.LocalNotificationSettingsRepositoryImpl
import com.eunilsung.talk.data.local.repository.LocalPushTokenRepositoryImpl
import com.eunilsung.talk.data.local.repository.LocalUserProfileRepositoryImpl
import com.eunilsung.talk.data.local.repository.LocalVoteRepositoryImpl
import com.eunilsung.talk.data.local.repository.LocalVersionCheckRepositoryImpl
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
import com.eunilsung.talk.ui.chatroom.linkpreview.LinkPreviewViewModel
import com.eunilsung.talk.ui.chatroomlist.group.ChatRoomGroupManageViewModel
import com.eunilsung.talk.ui.invite.InviteViewModel
import com.eunilsung.talk.ui.invite.tab.group.InviteGroupViewModel
import com.eunilsung.talk.ui.share.ShareViewModel
import com.eunilsung.talk.ui.login.LoginViewModel
import com.eunilsung.talk.ui.main.MainViewModel
import com.eunilsung.talk.ui.group.GroupViewModel
import com.eunilsung.talk.ui.quickactions.QuickActionsViewModel
import com.eunilsung.talk.ui.userprofile.UserProfileViewModel
import com.eunilsung.talk.ui.userprofile.ProfilePhotoPickerViewModel
import com.eunilsung.talk.ui.uikit.mediapicker.PhotoDetailViewModel
import com.eunilsung.talk.domain.repository.DeviceFileRepository
import com.eunilsung.talk.data.repository.DeviceFileRepositoryImpl
import com.eunilsung.talk.domain.repository.ProfileImageRepository
import com.eunilsung.talk.data.repository.ProfileImageRepositoryImpl
import com.eunilsung.talk.ui.chatroomlist.ChatRoomListViewModel
import com.eunilsung.talk.ui.setting.SettingViewModel
import com.eunilsung.talk.ui.chatroom.vote.VoteViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.module
import com.eunilsung.talk.data.remote.linkpreview.LinkPreviewRepositoryImpl
import com.eunilsung.talk.domain.repository.LinkPreviewRepository
import com.eunilsung.talk.data.remote.push.PushPayloadParser
import com.eunilsung.talk.data.repository.ScreenLockRepositoryImpl
import com.eunilsung.talk.domain.repository.ScreenLockRepository
import com.eunilsung.talk.domain.repository.NoticeUiStateRepository
import com.eunilsung.talk.domain.repository.CurrentChatRoomRepository
import com.eunilsung.talk.domain.repository.PendingPushNavigationRepository
import com.eunilsung.talk.domain.repository.PendingShareNavigationRepository
import com.eunilsung.talk.domain.repository.AppMaintenanceRepository
import com.eunilsung.talk.data.repository.AppMaintenanceRepositoryImpl
import com.eunilsung.talk.data.remote.push.CurrentChatRoomTracker
import com.eunilsung.talk.data.remote.push.PendingPushNavigation
import com.eunilsung.talk.data.remote.share.PendingShareNavigation
import com.eunilsung.talk.domain.repository.SharedFileCleaner
import com.eunilsung.talk.data.remote.share.PlatformSharedFileCleaner

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
    single { PushPayloadParser() }

    single<LinkPreviewRepository> { LinkPreviewRepositoryImpl(get()) }
    singleOf(::GetLinkPreviewUseCase)
    viewModelOf(::LinkPreviewViewModel)

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
    singleOf(::ObserveLoginStateUseCase)
    singleOf(::GetSavedLoginUseCase)
    singleOf(::SetSavePasswordUseCase)
    singleOf(::GetMyUserUseCase)
    singleOf(::LoginUseCases)

    viewModelOf(::MainViewModel)
    singleOf(::ObserveConnectionUseCase)
    singleOf(::SetForegroundUseCase)
    singleOf(::ReconnectUseCase)
    singleOf(::ObserveMainEventsUseCase)
    singleOf(::ObservePendingPushUseCase)
    singleOf(::ConsumePendingPushUseCase)
    singleOf(::ObservePendingShareUseCase)
    singleOf(::ConsumePendingShareUseCase)
    singleOf(::SetAppBadgeUseCase)
    singleOf(::IsImageCacheClearedOnUseCase)
    singleOf(::MarkImageCacheClearedUseCase)
    singleOf(::GetUnseenNoticeVersionUseCase)
    singleOf(::MarkNoticeShownUseCase)
    singleOf(::ObserveProfileImagesUseCase)
    singleOf(::MainUseCases)
    // 강제 로그아웃은 발생하지 않는다.
    single<MainRepository> { LocalMainRepositoryImpl() }

    viewModelOf(::GroupViewModel)
    viewModelOf(::QuickActionsViewModel)
    // TestAccounts / TestGroups 로 그룹 구성.
    singleOf(::UserDirectory)
    single<GroupRepository> {
        if (Config.Server.IS_ENABLED) {
            val directory: UserDirectory = get()
            val people: ServerPeopleMapper = get()
            val local = LocalGroupRepositoryImpl(
                get(), get(), get(), get(), get(),
                seedsSampleGroups = false,
                findUser = { userId, groupId -> directory.find(userId)?.let { people.toGroupUser(it, groupId) } },
            )
            GroupRepositoryImpl(local, get(), directory, get(), people)
        } else {
            LocalGroupRepositoryImpl(get(), get(), get(), get(), get())
        }
    }
    singleOf(::GroupMapper)
    singleOf(::SeedMapper)
    singleOf(::ServerPeopleMapper)
    singleOf(::GetGroupsUseCase)
    singleOf(::FetchGroupsUseCase)
    singleOf(::ToggleGroupExpandedUseCase)
    singleOf(::RemoveUserFromGroupUseCase)
    singleOf(::MoveUserToGroupUseCase)
    singleOf(::RenameGroupUseCase)
    singleOf(::DeleteGroupUseCase)
    singleOf(::CreateGroupUseCase)
    singleOf(::CopyUserToGroupUseCase)
    singleOf(::GroupUseCases)

    viewModelOf(::InviteViewModel)
    viewModelOf(::InviteGroupViewModel)
    // ChatRoomEntity 에 직접 방을 만들고 참여자를 추가.
    single<InviteRepository> {
        if (Config.Server.IS_ENABLED) InviteRepositoryImpl(get(), get()) else LocalInviteRepositoryImpl(get(), get(), get())
    }
    singleOf(::InviteUsersUseCase)
    singleOf(::InviteUseCases)

    single<SharedFileCleaner> { PlatformSharedFileCleaner() }
    singleOf(::CleanupSharedFileUseCase)
    viewModelOf(::ShareViewModel)

    viewModelOf(::UserProfileViewModel)
    viewModelOf(::ProfilePhotoPickerViewModel)
    single<ProfileImageRepository> { ProfileImageRepositoryImpl(get()) }
    singleOf(::SaveMyProfilePhotoUseCase)
    viewModelOf(::PhotoDetailViewModel)
    single<DeviceFileRepository> { DeviceFileRepositoryImpl(get(), get()) }
    singleOf(::DownloadFileUseCase)
    singleOf(::OpenFileUseCase)
    singleOf(::SaveToGalleryUseCase)
    // TestAccounts 를 프로필로 사용.
    single<UserProfileRepository> {
        if (Config.Server.IS_ENABLED) UserProfileRepositoryImpl(get(), get(), get()) else LocalUserProfileRepositoryImpl(get())
    }

    singleOf(::ObserveNewChatUseCase)
    singleOf(::ObserveMySendUseCase)
    singleOf(::ObserveLatestLoadedUseCase)
    singleOf(::ObserveSelfLeftUseCase)
    singleOf(::ObserveRoomUsersChangedUseCase)
    singleOf(::ObserveCurrentNoticeUseCase)
    singleOf(::ObserveBookmarksUseCase)
    singleOf(::ClearCurrentRoomUseCase)
    singleOf(::IsCurrentChatRoomUseCase)
    singleOf(::GetLastEmoticonTabUseCase)
    singleOf(::SetLastEmoticonTabUseCase)
    singleOf(::GetSenderUseCase)
    singleOf(::SelectSenderUseCase)
    singleOf(::GetNoticeBarStateUseCase)
    singleOf(::SetNoticeCollapsedUseCase)
    singleOf(::SetNoticeDetailsShownUseCase)
    singleOf(::HideNoticeUseCase)
    singleOf(::OpenChatRoomUseCase)
    singleOf(::CloseChatRoomUseCase)
    singleOf(::CancelChatRoomNotificationUseCase)
    singleOf(::ChatRoomStateUseCases)
    viewModelOf(::ChatRoomViewModel)
    single<NoticeUiStateRepository> { NoticeUiStateStore(get()) }
    single<CurrentChatRoomRepository> { CurrentChatRoomTracker }
    single<PendingPushNavigationRepository> { PendingPushNavigation }
    single<PendingShareNavigationRepository> { PendingShareNavigation }
    single<AppMaintenanceRepository> { AppMaintenanceRepositoryImpl(get(), get()) }

    singleOf(::ChatMapper)
    // ChatEntity 에 대화를 직접 읽고 쓴다. (투표 저장소도 구현 타입이 필요해 둘 다 등록)
    // 전송 주체 전환(샘플 전용) — 싱글턴.
    single<SenderOverrideRepository> { ChatSenderOverride() }
    single {
        LocalChatRoomRepositoryImpl(
            get(), get(), get(), get<LocalChatRoomListRepositoryImpl>(), get(), get(), get(),
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
    singleOf(::PolishTextUseCase)
    singleOf(::TranslateChatUseCase)
    singleOf(::FetchBookmarksUseCase)
    singleOf(::AddBookmarkUseCase)
    singleOf(::DeleteBookmarkUseCase)
    /** 인자가 22개를 넘어 `singleOf` 로는 등록할 수 없다. */
    single {
        ChatRoomUseCases(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
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
    viewModelOf(::ChatRoomGroupManageViewModel)

    // 알림 설정은 로컬 저장만 한다.
    single<NotificationSettingsRepository> { LocalNotificationSettingsRepositoryImpl(get(), get()) }
    single<ChatSettingsRepository> { ChatSettingsRepositoryImpl(get()) }
    single<ScreenLockRepository> {
        ScreenLockRepositoryImpl(get())
    }
    singleOf(::ObserveNotificationSettingsUseCase)
    singleOf(::SyncNotificationFromSystemUseCase)
    singleOf(::OpenSystemNotificationSettingsUseCase)
    singleOf(::SetNotificationPreviewUseCase)
    singleOf(::SetNotificationTimeLimitUseCase)
    singleOf(::SetNotificationPcOffUseCase)
    singleOf(::SetNotificationSoundUseCase)
    singleOf(::SetNotificationVibrateUseCase)
    singleOf(::SetNotificationSoundIdUseCase)
    singleOf(::PlaySoundPreviewUseCase)
    singleOf(::StopSoundPreviewUseCase)
    singleOf(::ObserveChatSettingsUseCase)
    singleOf(::SetChatEnterToSendUseCase)
    singleOf(::SetChatFontSizeUseCase)
    singleOf(::SettingUseCases)
    singleOf(::ObserveScreenLockConfigUseCase)
    singleOf(::DisableScreenLockUseCase)
    singleOf(::SetScreenLockPasswordUseCase)
    singleOf(::SetScreenLockPatternUseCase)
    singleOf(::VerifyScreenLockPasswordUseCase)
    singleOf(::VerifyScreenLockPatternUseCase)
    singleOf(::SetScreenLockPatternVisibleUseCase)
    singleOf(::SetScreenLockBiometricUseCase)
    singleOf(::ClearScreenLockBiometricUseCase)
    singleOf(::IsBiometricAvailableUseCase)
    singleOf(::AuthenticateBiometricUseCase)
    singleOf(::ScreenLockUseCases)
    singleOf(::GetCachedProfileUseCase)
    singleOf(::FetchProfileUseCase)
    singleOf(::UserProfileUseCases)
    viewModelOf(::ScreenLockViewModel)
    viewModelOf(::SettingViewModel)

    viewModelOf(::VoteViewModel)
    // 투표/투표 결과는 VoteEntity 에 저장.
    single<VoteRepository> {
        if (Config.Server.IS_ENABLED) VoteRepositoryImpl(get(), get(), get(), get()) else LocalVoteRepositoryImpl(get(), get(), get())
    }
    singleOf(::VoteMapper)
    singleOf(::GetVotesUseCase)
    singleOf(::GetVoteUseCase)
    singleOf(::CreateVoteUseCase)
    singleOf(::SubmitVoteUseCase)
    singleOf(::ReVoteUseCase)
    singleOf(::CloseVoteUseCase)
    singleOf(::VoteUseCases)
}

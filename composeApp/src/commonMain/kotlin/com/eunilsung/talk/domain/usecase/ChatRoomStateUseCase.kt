package com.eunilsung.talk.domain.usecase

import com.eunilsung.talk.domain.model.Bookmark
import com.eunilsung.talk.domain.model.Notice
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.repository.ChatRoomRepository
import com.eunilsung.talk.domain.repository.ChatSettingsRepository
import com.eunilsung.talk.domain.repository.CurrentChatRoomRepository
import com.eunilsung.talk.domain.repository.NoticeUiStateRepository
import com.eunilsung.talk.domain.repository.PushNotifier
import com.eunilsung.talk.domain.repository.SenderOverrideRepository
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 대화방 화면이 지켜보는 것과 화면에 딸린 설정의 유스케이스 묶음.
 *
 * 대화를 보내고 받는 일은 [ChatRoomUseCases] 에 있다. 여기에는 저장소가 밀어 주는 신호, 공지·책갈피의
 * 현재 값, 그리고 공지바·보내는 사람·이모티콘 탭처럼 화면이 기억해 두는 것들이 모여 있다.
 */
data class ChatRoomStateUseCases(
    val observeNewChat: ObserveNewChatUseCase,
    val observeMySend: ObserveMySendUseCase,
    val observeLatestLoaded: ObserveLatestLoadedUseCase,
    val observeSelfLeft: ObserveSelfLeftUseCase,
    val observeRoomUsersChanged: ObserveRoomUsersChangedUseCase,
    val observeCurrentNotice: ObserveCurrentNoticeUseCase,
    val observeBookmarks: ObserveBookmarksUseCase,
    val clearCurrentRoom: ClearCurrentRoomUseCase,
    val isCurrentChatRoom: IsCurrentChatRoomUseCase,
    val observeChatSettings: ObserveChatSettingsUseCase,
    val getLastEmoticonTab: GetLastEmoticonTabUseCase,
    val setLastEmoticonTab: SetLastEmoticonTabUseCase,
    val getSender: GetSenderUseCase,
    val selectSender: SelectSenderUseCase,
    val getNoticeBarState: GetNoticeBarStateUseCase,
    val setNoticeCollapsed: SetNoticeCollapsedUseCase,
    val setNoticeDetailsShown: SetNoticeDetailsShownUseCase,
    val hideNotice: HideNoticeUseCase,
    val openChatRoom: OpenChatRoomUseCase,
    val closeChatRoom: CloseChatRoomUseCase,
    val cancelChatRoomNotification: CancelChatRoomNotificationUseCase,
    val getMyUser: GetMyUserUseCase,
)

/** 상대의 새 대화가 들어왔다는 신호. 값은 대화방 id. */
class ObserveNewChatUseCase(private val repository: ChatRoomRepository) {
    operator fun invoke(): SharedFlow<String> = repository.newChatPush
}

/** 내가 대화를 보냈다는 신호. 값은 대화방 id. */
class ObserveMySendUseCase(private val repository: ChatRoomRepository) {
    operator fun invoke(): SharedFlow<String> = repository.mySendPush
}

/** 최신 대화를 다시 불러왔다는 신호. 값은 대화방 id. */
class ObserveLatestLoadedUseCase(private val repository: ChatRoomRepository) {
    operator fun invoke(): SharedFlow<String> = repository.latestLoadedPush
}

/** 내가 그 방에서 나가졌다는 신호. 값은 대화방 id. */
class ObserveSelfLeftUseCase(private val repository: ChatRoomRepository) {
    operator fun invoke(): SharedFlow<String> = repository.selfLeftPush
}

/** 방의 참여자가 바뀌었다는 신호. 값은 대화방 id. */
class ObserveRoomUsersChangedUseCase(private val repository: ChatRoomRepository) {
    operator fun invoke(): SharedFlow<String> = repository.roomUsersChanged
}

/** 지금 보고 있는 방에 걸린 공지. 없으면 null. */
class ObserveCurrentNoticeUseCase(private val repository: ChatRoomRepository) {
    operator fun invoke(): StateFlow<Notice?> = repository.currentNotice
}

/** 지금 보고 있는 방의 책갈피. */
class ObserveBookmarksUseCase(private val repository: ChatRoomRepository) {
    operator fun invoke(): StateFlow<List<Bookmark>> = repository.bookmarks
}

/** 방을 닫을 때 그 방에 딸려 들고 있던 것(공지·책갈피)을 비운다. */
class ClearCurrentRoomUseCase(private val repository: ChatRoomRepository) {
    operator fun invoke(chatRoomId: String) = repository.clearCurrentRoom(chatRoomId)
}

/** 방을 화면에 띄웠다. 그 방의 알림을 걷고, 그 방의 새 대화는 알림 없이 읽은 것으로 치게 한다. */
class OpenChatRoomUseCase(
    private val currentRoom: CurrentChatRoomRepository,
    private val notifier: PushNotifier,
) {
    operator fun invoke(chatRoomId: String) {
        currentRoom.set(chatRoomId)
        notifier.cancel(chatRoomId)
    }
}

/**
 * 방을 화면에서 내렸다. 그 사이 다른 방이 떠 있으면 그 방은 그대로 둔다.
 *
 * 그 방의 알림을 걷고, 그 방에 딸려 들고 있던 것(공지·책갈피)을 비운다.
 */
class CloseChatRoomUseCase(
    private val currentRoom: CurrentChatRoomRepository,
    private val notifier: PushNotifier,
    private val repository: ChatRoomRepository,
) {
    operator fun invoke(chatRoomId: String) {
        if (currentRoom.currentChatRoomId == chatRoomId) currentRoom.clear()
        notifier.cancel(chatRoomId)
        repository.clearCurrentRoom(chatRoomId)
    }
}

/** 그 방의 알림을 걷는다. */
class CancelChatRoomNotificationUseCase(private val notifier: PushNotifier) {
    operator fun invoke(chatRoomId: String) = notifier.cancel(chatRoomId)
}

/** 그 방이 지금 화면에 떠 있는지. 떠 있는 방의 새 대화는 바로 읽은 것으로 친다. */
class IsCurrentChatRoomUseCase(private val repository: CurrentChatRoomRepository) {
    operator fun invoke(chatRoomId: String): Boolean = repository.currentChatRoomId == chatRoomId
}

/** 마지막으로 고른 이모티콘 탭. */
class GetLastEmoticonTabUseCase(private val repository: ChatSettingsRepository) {
    operator fun invoke(): Int = repository.getLastEmoticonTab()
}

class SetLastEmoticonTabUseCase(private val repository: ChatSettingsRepository) {
    operator fun invoke(index: Int) = repository.setLastEmoticonTab(index)
}

/** 그 방에서 누구로 보낼지. null 이면 나. 서버가 없는 로컬 모드에서만 쓰인다. */
class GetSenderUseCase(private val repository: SenderOverrideRepository) {
    operator fun invoke(chatRoomId: String): User? = repository.senderFor(chatRoomId)
}

class SelectSenderUseCase(private val repository: SenderOverrideRepository) {
    operator fun invoke(chatRoomId: String, user: User?) = repository.select(chatRoomId, user)
}

/** 공지바를 어떻게 보이게 해 두었는지. */
data class NoticeBarState(val hidden: Boolean, val collapsed: Boolean, val detailsShown: Boolean)

class GetNoticeBarStateUseCase(private val repository: NoticeUiStateRepository) {
    operator fun invoke(identityKey: String) = NoticeBarState(
        hidden = repository.isHidden(identityKey),
        collapsed = repository.isCollapsed(identityKey),
        detailsShown = repository.isDetailsShown(identityKey),
    )
}

class SetNoticeCollapsedUseCase(private val repository: NoticeUiStateRepository) {
    operator fun invoke(identityKey: String, collapsed: Boolean) = repository.setCollapsed(identityKey, collapsed)
}

class SetNoticeDetailsShownUseCase(private val repository: NoticeUiStateRepository) {
    operator fun invoke(identityKey: String, shown: Boolean) = repository.setDetailsShown(identityKey, shown)
}

/** 이 공지를 다시 보지 않는다. */
class HideNoticeUseCase(private val repository: NoticeUiStateRepository) {
    operator fun invoke(identityKey: String) = repository.setHidden(identityKey, true)
}

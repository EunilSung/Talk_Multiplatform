package com.eunilsung.talk.ui.main

import com.eunilsung.talk.Config
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import com.eunilsung.talk.domain.model.MainEvent
import com.eunilsung.talk.domain.model.OpenFileResult
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.domain.usecase.ChatRoomListUseCases
import com.eunilsung.talk.domain.usecase.GroupUseCases
import com.eunilsung.talk.domain.usecase.LoginUseCases
import com.eunilsung.talk.domain.usecase.MainUseCases
import com.eunilsung.talk.util.Log

class MainViewModel(
    private val mainUseCases: MainUseCases,
    private val loginUseCases: LoginUseCases,
    private val groupUseCases: GroupUseCases,
    private val chatRoomListUseCases: ChatRoomListUseCases,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> =
        combine(
            loginUseCases.observeLoginState(),
            mainUseCases.observeConnection(),
            chatRoomListUseCases.observeChatRoomUnreadTotal(),
            mainUseCases.observeProfileImages(),
        ) { isLoggedIn, isConnected, unreadTotal, profileImages ->
            MainUiState(
                isLoggedIn = isLoggedIn,
                isConnected = isConnected,
                chatUnreadTotal = unreadTotal,
                profileImages = profileImages,
                me = if (isLoggedIn) loginUseCases.getMyUser() else User(),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            MainUiState(
                isLoggedIn = loginUseCases.observeLoginState().value,
                isConnected = mainUseCases.observeConnection().value,
                chatUnreadTotal = chatRoomListUseCases.observeChatRoomUnreadTotal().value,
                profileImages = mainUseCases.observeProfileImages().value,
                me = loginUseCases.getMyUser(),
            ),
        )

    private val _events = Channel<MainScreenEvent>(Channel.BUFFERED)
    val events: Flow<MainScreenEvent> = _events.receiveAsFlow()

    init {
        checkImageCache()

        viewModelScope.launch {
            loginUseCases.observeLoginState()
                .filter { it }
                .collect {
                    Log.message("Login success — prefetching tab data")
                    launch { runCatching { groupUseCases.fetchGroups() } }
                    launch { runCatching { chatRoomListUseCases.fetchChatRooms() } }
                    unseenNoticeVersion()?.let { _events.send(MainScreenEvent.ShowNotice(it)) }
                }
        }

        viewModelScope.launch {
            loginUseCases.observeLoginState()
                .drop(1)
                .filter { !it }
                .collect {
                    mainUseCases.consumePendingPush()
                    mainUseCases.consumePendingShare()
                    _events.send(MainScreenEvent.LoggedOut)
                }
        }

        viewModelScope.launch {
            mainUseCases.observeMainEvents().collect { event ->
                when (event) {
                    is MainEvent.ForcedLogout -> _events.send(MainScreenEvent.ForcedLogout(event.message))
                }
            }
        }

        viewModelScope.launch {
            combine(
                loginUseCases.observeLoginState(),
                chatRoomListUseCases.observeChatRoomUnreadTotal(),
            ) { loggedIn, chatUnread ->
                if (loggedIn) chatUnread else 0
            }.distinctUntilChanged().collect { total ->
                mainUseCases.setAppBadge(total)
            }
        }

        routePendingPush()
        routePendingShare()
    }

    fun onAction(action: MainActions) {
        when (action) {
            is MainActions.OnForeground -> {
                Log.message("Foreground")
                mainUseCases.setForeground(true)
                mainUseCases.reconnect()
            }
            is MainActions.OnBackground -> {
                Log.message("Background")
                mainUseCases.setForeground(false)
            }
            is MainActions.OnImageCacheCleared -> {
                mainUseCases.markImageCacheCleared(action.date)
                Log.message("Image cache cleared date=${action.date} keepDaily=${Config.UserProfile.IS_KEEP_CACHE_DAY}")
            }
            is MainActions.OnNoticeConfirmed -> mainUseCases.markNoticeShown(action.version)
            is MainActions.Logout -> loginUseCases.logout()
            is MainActions.OnDownloadFile -> downloadFile(action.source, action.displayName)
            is MainActions.OnOpenFile -> openFile(action.source, action.displayName)
        }
    }

    private fun downloadFile(source: String, displayName: String) {
        viewModelScope.launch {
            val saved = mainUseCases.downloadFile(source, displayName)
            _events.send(
                if (saved) MainScreenEvent.FileDownloaded(displayName) else MainScreenEvent.FileDownloadFailed
            )
        }
    }

    private fun openFile(source: String, displayName: String) {
        viewModelScope.launch {
            when (mainUseCases.openFile(source, displayName)) {
                OpenFileResult.OPENED -> Unit
                OpenFileResult.DOWNLOADED_AND_OPENED -> _events.send(MainScreenEvent.FileDownloaded(displayName))
                OpenFileResult.FAILED -> _events.send(MainScreenEvent.FileOpenFailed)
            }
        }
    }

    /**
     * 이미지 캐시를 오늘 아직 비우지 않았으면 비우라고 알린다.
     *
     * [Config.UserProfile.IS_KEEP_CACHE_DAY] 가 false 면 하루 1회 제한을 두지 않고
     * 앱 실행마다 비운다.
     */
    private fun checkImageCache() {
        val today = Instant
            .fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date.toString()
        if (Config.UserProfile.IS_KEEP_CACHE_DAY && mainUseCases.isImageCacheClearedOn(today)) {
            Log.message("Image cache clear skipped — already cleared today ($today)")
            return
        }
        _events.trySend(MainScreenEvent.ClearImageCache(today))
    }

    /**
     * 이번 버전의 안내 팝업을 아직 안 봤으면 버전 문자열, 이미 봤거나 버전을 모르면 null.
     *
     * 안내는 "서버 없이 기기 안 데이터로 도는 샘플"이라는 내용이라 서버 모드에서는 띄우지 않는다.
     */
    private fun unseenNoticeVersion(): String? {
        if (Config.Server.IS_ENABLED) return null
        return mainUseCases.getUnseenNoticeVersion()
    }

    /**
     * 푸시 알림을 눌러 들어온 화면을 로그인한 뒤에 연다.
     *
     * 연결이 끊겨 있어도 기다리지 않고 들어간다. 다시 붙는 것은 화면이 처음 조회할 때 따라온다.
     */
    private fun routePendingPush() {
        viewModelScope.launch {
            combine(
                mainUseCases.observePendingPush(),
                loginUseCases.observeLoginState(),
            ) { pending, isLoggedIn ->
                if (pending != null && !isLoggedIn) {
                    Log.message("[PushNav] waiting — not logged in (key=${pending.msgKey})")
                }
                pending.takeIf { isLoggedIn }
            }.filterNotNull().collect { req ->
                val isConnected = mainUseCases.observeConnection().value
                if (!isConnected) {
                    Log.message("[PushNav] socket disconnected — entering screen anyway, reconnect will happen on first fetch (key=${req.msgKey})")
                    runCatching { mainUseCases.reconnect() }
                }
                val consumed = mainUseCases.consumePendingPush() ?: return@collect
                Log.message("[PushNav] routing — kind=${consumed.kind} key=${consumed.msgKey} isConnected=$isConnected")
                _events.send(MainScreenEvent.OpenPush(consumed))
            }
        }
    }

    /** 다른 앱에서 공유해 들어온 내용을 로그인한 뒤에 넘긴다. */
    private fun routePendingShare() {
        viewModelScope.launch {
            combine(
                mainUseCases.observePendingShare(),
                loginUseCases.observeLoginState(),
            ) { pending, isLoggedIn ->
                if (pending != null && !isLoggedIn) {
                    Log.message("[ShareNav] waiting — not logged in (text=${pending.text.length} files=${pending.filePaths.size})")
                }
                pending.takeIf { isLoggedIn }
            }.filterNotNull().collect {
                val consumed = mainUseCases.consumePendingShare() ?: return@collect
                Log.message("[ShareNav] routing — text=${consumed.text.length} files=${consumed.filePaths.size}")
                _events.send(MainScreenEvent.OpenShare(consumed))
            }
        }
    }
}

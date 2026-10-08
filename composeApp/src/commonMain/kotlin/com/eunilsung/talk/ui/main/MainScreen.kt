package com.eunilsung.talk.ui.main

import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.main_notice_message
import org.jetbrains.compose.resources.getString
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import com.eunilsung.talk.domain.model.MainEvent
import com.eunilsung.talk.domain.repository.LoginRepository
import com.eunilsung.talk.ui.login.LoginScreen
import com.eunilsung.talk.ui.main.tab.ChatTab
import com.eunilsung.talk.ui.main.tab.GroupTab
import com.eunilsung.talk.ui.main.tab.SettingTab
import com.eunilsung.talk.ui.main.tab.CurrentAppTab
import com.eunilsung.talk.ui.main.tab.TabItem
import com.eunilsung.talk.ui.main.tab.rememberAppTabState
import com.eunilsung.talk.ui.theme.AppTheme
import com.eunilsung.talk.ui.uikit.BackHandler
import com.eunilsung.talk.ui.uikit.dialog.LocalDialogManager
import com.eunilsung.talk.ui.uikit.line.LineDivider
import com.eunilsung.talk.Platform
import androidx.compose.runtime.getValue
import com.eunilsung.talk.ui.theme.AppColors
import kotlinx.datetime.toLocalDateTime
import com.eunilsung.talk.Config
import com.eunilsung.talk.ui.screenlock.lock.ScreenLockGate
import com.eunilsung.talk.ui.uikit.swipe.LocalSwipeDismiss
import com.eunilsung.talk.ui.uikit.swipe.SwipeDismissController
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.eunilsung.talk.util.Log

object MainScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: MainViewModel = koinViewModel()
        val loginRepository: LoginRepository = koinInject()
        val isLoggedIn by loginRepository.isLoggedIn.collectAsState()

        val imageContext = coil3.compose.LocalPlatformContext.current
        LaunchedEffect(Unit) {
            val today = kotlinx.datetime.Instant
                .fromEpochMilliseconds(kotlin.time.Clock.System.now().toEpochMilliseconds())
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                .date.toString()
            if (viewModel.shouldClearImageCache(today)) {
                coil3.SingletonImageLoader.get(imageContext).apply {
                    memoryCache?.clear()
                    diskCache?.clear()
                }
                viewModel.markImageCacheCleared(today)
                Log.message("Image cache cleared date=$today keepDaily=${Config.UserProfile.IS_KEEP_CACHE_DAY}")
            } else {
                Log.message("Image cache clear skipped — already cleared today ($today)")
            }
        }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            Log.message("Foreground")
            loginRepository.isForeground.value = true
            loginRepository.checkConnectionAndRelogin()
        }

        LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
            Log.message("Background")
            loginRepository.isForeground.value = false
        }

        AppTheme {
            Navigator(EmptyScreen) { rightNavigator ->
                LaunchedEffect(Unit) {
                    loginRepository.isLoggedIn
                        .drop(1)
                        .filter { !it }
                        .collect {
                            rightNavigator.popToEmptyRoot()
                            com.eunilsung.talk.data.remote.push.PendingPushNavigation.consume()
                            com.eunilsung.talk.data.remote.share.PendingShareNavigation.consume()
                        }
                }

                AppHosts(rightNavigator) {
                    ForcedLogoutObserver(viewModel, loginRepository)
                    AppIconBadgeSync()
                    MainNoticeDialog(viewModel)
                    MainContent()
                    if (!isLoggedIn) LoginScreen()
                    PendingPushNavigationHandler(rightNavigator, loginRepository)
                    PendingShareNavigationHandler(loginRepository)
                    ScreenLockGate()
                }
            }
        }
    }
}


/** 앱 아이콘(런처) 배지 동기화 — 대화 안읽음 총합을 배지에 반영. */
@Composable
private fun AppIconBadgeSync() {
    val loginRepository: LoginRepository = koinInject()
    val chatRoomListUseCases: com.eunilsung.talk.domain.usecase.ChatRoomListUseCases = koinInject()
    val notifier: com.eunilsung.talk.data.remote.push.PushNotifier = koinInject()

    LaunchedEffect(Unit) {
        combine(
            loginRepository.isLoggedIn,
            chatRoomListUseCases.observeChatRoomUnreadTotal(),
        ) { loggedIn, chatUnread ->
            if (loggedIn) chatUnread else 0
        }.distinctUntilChanged().collect { total ->
            notifier.setBadge(total)
        }
    }
}

/** 로그인 후 메인 진입 시 버전명 기준 1회 공지. */
@Composable
private fun MainNoticeDialog(viewModel: MainViewModel) {
    val loginRepository: LoginRepository = koinInject()
    val dialog = LocalDialogManager.current
    val isLoggedIn by loginRepository.isLoggedIn.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        val version = viewModel.pendingNoticeVersion() ?: return@LaunchedEffect
        dialog.alert(
            message = getString(Res.string.main_notice_message),
            onConfirm = { viewModel.markNoticeShown(version) },
        )
    }
}

@Composable
private fun ForcedLogoutObserver(viewModel: MainViewModel, loginRepository: LoginRepository) {
    val dialog = LocalDialogManager.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MainEvent.ForcedLogout -> dialog.alert(
                    message = event.message,
                    onConfirm = { loginRepository.logout() }
                )
            }
        }
    }
}

@Composable
fun rememberImeHeight(): Dp {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeBottom = imeInsets.getBottom(density)
    return with(density) { imeBottom.toDp() }
}

@Composable
fun MainContent() {
    val imeHeight = rememberImeHeight()
    val isKeyboardVisible = imeHeight > 0.dp
    val rightNavigator = LocalRightNavigator.current

    val rightPaneOpen = rightNavigator.lastItem !is EmptyScreen

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        androidx.compose.runtime.CompositionLocalProvider(LocalRightPaneOpen provides rightPaneOpen) {
            if (isTablet) {
                Tablet(rightNavigator, isKeyboardVisible, imeHeight)
            } else {
                Phone(rightNavigator, isKeyboardVisible, imeHeight)
            }
        }
    }
}

@Composable
fun TabletLayout(main: @Composable () -> Unit, sub: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { main() }
        LineDivider(modifier = Modifier.width(1.dp).fillMaxHeight())
        Box(modifier = Modifier.weight(1.2f).fillMaxHeight().clipToBounds()) { sub() }
    }
}

@Composable
fun Tablet(
    rightNavigator: Navigator,
    isKeyboardVisible: Boolean,
    imeHeight: Dp,
) {
    TabletLayout(
        main = {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) { LeftPane() }
                KeyBoardPane(
                    modifier = Modifier.background(AppColors.Gray50Bg),
                    isKeyboardVisible = isKeyboardVisible,
                    imeHeight = imeHeight,
                    fillIdleBottomInset = true,
                )
            }
        },
        sub = {
            AnimatedContent(
                targetState = rightNavigator.lastItem,
                transitionSpec = {
                    val (enter, exit) = if (rightNavigator.lastEvent == StackEvent.Pop) {
                        (slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300))) to
                                (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)))
                    } else {
                        (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) to
                                (slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300)))
                    }
                    enter togetherWith exit
                }
            ) { screen ->
                screen.Content()
            }
        }
    )
}

@Composable
fun LeftPane() {
    val tabList = listOf(GroupTab, ChatTab, SettingTab)
    val platform: Platform = koinInject()
    val isIos = platform.name == "IPHONE"
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val loginRepository: LoginRepository = koinInject()
    val isLoggedIn by loginRepository.isLoggedIn.collectAsState()

    val chatRoomListUseCases: com.eunilsung.talk.domain.usecase.ChatRoomListUseCases = koinInject()
    val chatUnreadTotal by chatRoomListUseCases.observeChatRoomUnreadTotal().collectAsState()

    key(isLoggedIn) {
        val tabState = rememberAppTabState(tabList, GroupTab)
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column(modifier = Modifier.background(color = MaterialTheme.colorScheme.background)) {
                    LineDivider(modifier = Modifier.fillMaxWidth().height(1.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .height(70.dp)
                            .background(AppColors.Gray50Bg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (tab in tabList) {
                            TabItem(
                                tab = tab,
                                modifier = Modifier.weight(1f),
                                isSelected = tabState.current == tab,
                                badgeCount = when (tab) {
                                    ChatTab -> chatUnreadTotal
                                    else -> 0
                                },
                                onClick = { tabState.current = tab }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = AppColors.Transparent)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    }
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                CurrentAppTab(tabState)
            }
        }
    }
}

private const val OverlayParallaxFraction = 0.3f

@Composable
fun Phone(
    rightNavigator: Navigator,
    isKeyboardVisible: Boolean,
    imeHeight: Dp,
) {
    val current = rightNavigator.lastItem
    val hasOverlay = current !is EmptyScreen
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    if (hasOverlay) {
        BackHandler { rightNavigator.popToEmptyRoot() }
    }

    var stickyOverlay by remember { mutableStateOf<Screen?>(null) }
    SideEffect {
        if (current !is EmptyScreen) {
            stickyOverlay = current
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val edgeWidthPx = screenWidthPx
        val swipeStartThresholdPx = with(density) { 60.dp.toPx() }

        val offsetX = remember { Animatable(screenWidthPx) }

        LaunchedEffect(hasOverlay, screenWidthPx) {
            val target = if (hasOverlay) 0f else screenWidthPx
            offsetX.animateTo(target, tween(durationMillis = 300))
        }

        var swipeStartOffset by remember { mutableStateOf(0f) }
        val swipeController = remember(screenWidthPx) {
            SwipeDismissController(
                onSwipeStart = {
                    swipeStartOffset = offsetX.value
                },
                onSwipeProgress = { totalDx ->
                    scope.launch {
                        offsetX.snapTo((swipeStartOffset + totalDx).coerceAtLeast(0f))
                    }
                },
                onSwipeEnd = { velocityX ->
                    val shouldDismiss =
                        offsetX.value > screenWidthPx * 0.15f || velocityX > 1000f
                    scope.launch {
                        if (shouldDismiss) {
                            offsetX.animateTo(screenWidthPx, tween(durationMillis = 200))
                            rightNavigator.popToEmptyRoot()
                        } else {
                            offsetX.animateTo(0f, spring())
                        }
                    }
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    val bg = -OverlayParallaxFraction * (screenWidthPx - offsetX.value)
                    IntOffset(bg.roundToInt(), 0)
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) { LeftPane() }
                KeyBoardPane(
                    modifier = Modifier.background(AppColors.Gray50Bg),
                    isKeyboardVisible = isKeyboardVisible,
                    imeHeight = imeHeight,
                    fillIdleBottomInset = true,
                )
            }
        }

        // offsetX.value 를 직접 읽으면 슬라이드 300ms 동안 Phone 전체가 매 프레임 재구성된다.
        // 자식 stickyOverlay 는 Voyager Screen(인터페이스, unstable)이라 skip 되지 않아 비용이 크다.
        val overlayVisible by remember(offsetX, screenWidthPx) {
            derivedStateOf { offsetX.value < screenWidthPx }
        }
        if (overlayVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(hasOverlay, screenWidthPx, edgeWidthPx, swipeStartThresholdPx) {
                        if (!hasOverlay) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (down.position.x > edgeWidthPx) return@awaitEachGesture

                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPointerInputChange(down)

                            var dragStarted = false
                            while (!dragStarted) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@awaitEachGesture
                                if (!change.pressed) return@awaitEachGesture
                                if (change.isConsumed) return@awaitEachGesture
                                velocityTracker.addPointerInputChange(change)
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (kotlin.math.abs(dy) > swipeStartThresholdPx &&
                                    kotlin.math.abs(dy) > kotlin.math.abs(dx)
                                ) {
                                    return@awaitEachGesture
                                }
                                if (dx >= swipeStartThresholdPx) {
                                    dragStarted = true
                                    change.consume()
                                    scope.launch {
                                        offsetX.snapTo((offsetX.value + dx).coerceAtLeast(0f))
                                    }
                                }
                            }

                            horizontalDrag(down.id) { change ->
                                velocityTracker.addPointerInputChange(change)
                                val delta = change.positionChange().x
                                scope.launch {
                                    offsetX.snapTo((offsetX.value + delta).coerceAtLeast(0f))
                                }
                                change.consume()
                            }

                            val velocityX = velocityTracker.calculateVelocity().x
                            val shouldDismiss =
                                offsetX.value > screenWidthPx * 0.3f || velocityX > 1500f
                            scope.launch {
                                if (shouldDismiss) {
                                    offsetX.animateTo(screenWidthPx, tween(durationMillis = 200))
                                    rightNavigator.popToEmptyRoot()
                                } else {
                                    offsetX.animateTo(0f, spring())
                                }
                            }
                        }
                    }
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSwipeDismiss provides swipeController,
                ) {
                    stickyOverlay?.Content()
                }
            }
        }
    }
}

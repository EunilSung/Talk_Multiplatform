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
import kotlinx.coroutines.launch
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
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
import androidx.compose.runtime.getValue
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.screenlock.lock.ScreenLockGate
import com.eunilsung.talk.ui.uikit.swipe.LocalSwipeDismiss
import com.eunilsung.talk.ui.uikit.swipe.SwipeDismissController
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.eunilsung.talk.ui.share.ShareScreen
import com.eunilsung.talk.ui.uikit.sheet.LocalBottomSheetManager

object MainScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: MainViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.onAction(MainActions.OnForeground)
        }

        LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
            viewModel.onAction(MainActions.OnBackground)
        }

        AppTheme {
            Navigator(EmptyScreen) { rightNavigator ->
                AppHosts(
                    rightNavigator = rightNavigator,
                    isReconnecting = uiState.isReconnecting,
                    isLoggedIn = uiState.isLoggedIn,
                ) {
                    MainEventHandler(viewModel, rightNavigator)
                    MainContent(isLoggedIn = uiState.isLoggedIn, chatUnreadTotal = uiState.chatUnreadTotal)
                    if (!uiState.isLoggedIn) LoginScreen()
                    ScreenLockGate(isLoggedIn = uiState.isLoggedIn)
                }
            }
        }
    }
}

/** [MainViewModel] 이 보내는 일을 처리한다. 다이얼로그·시트·오버레이를 써야 해서 [AppHosts] 안에 둔다. */
@Composable
private fun MainEventHandler(viewModel: MainViewModel, rightNavigator: Navigator) {
    val dialog = LocalDialogManager.current
    val bottomSheet = LocalBottomSheetManager.current
    val showOverlay = LocalFullScreenOverlay.current
    val imageContext = coil3.compose.LocalPlatformContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MainScreenEvent.ClearImageCache -> {
                    coil3.SingletonImageLoader.get(imageContext).apply {
                        memoryCache?.clear()
                        diskCache?.clear()
                    }
                    viewModel.onAction(MainActions.OnImageCacheCleared(event.date))
                }
                is MainScreenEvent.ShowNotice -> dialog.alert(
                    message = getString(Res.string.main_notice_message),
                    onConfirm = { viewModel.onAction(MainActions.OnNoticeConfirmed(event.version)) },
                )
                is MainScreenEvent.ForcedLogout -> dialog.alert(
                    message = event.message,
                    onConfirm = { viewModel.onAction(MainActions.Logout) },
                )
                is MainScreenEvent.LoggedOut -> rightNavigator.popToEmptyRoot()
                is MainScreenEvent.OpenPush -> {
                    dialog.dismiss()
                    bottomSheet.hide()
                    showOverlay(null)
                    routePushNavigation(rightNavigator, event.request)
                }
                is MainScreenEvent.OpenShare -> showOverlay(ShareScreen(event.content))
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
fun MainContent(isLoggedIn: Boolean, chatUnreadTotal: Int) {
    val imeHeight = rememberImeHeight()
    val isKeyboardVisible = imeHeight > 0.dp
    val rightNavigator = LocalRightNavigator.current

    val rightPaneOpen = rightNavigator.lastItem !is EmptyScreen

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        CompositionLocalProvider(LocalRightPaneOpen provides rightPaneOpen) {
            if (isTablet) {
                Tablet(rightNavigator, isKeyboardVisible, imeHeight, isLoggedIn, chatUnreadTotal)
            } else {
                Phone(rightNavigator, isKeyboardVisible, imeHeight, isLoggedIn, chatUnreadTotal)
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
    isLoggedIn: Boolean,
    chatUnreadTotal: Int,
) {
    TabletLayout(
        main = {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) { LeftPane(isLoggedIn, chatUnreadTotal) }
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

/**
 * 하단 탭과 탭 본문.
 *
 * @param isLoggedIn 바뀌면 탭 선택을 처음으로 되돌린다.
 * @param chatUnreadTotal 대화 탭 배지.
 */
@Composable
fun LeftPane(isLoggedIn: Boolean, chatUnreadTotal: Int) {
    val tabList = listOf(GroupTab, ChatTab, SettingTab)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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
    isLoggedIn: Boolean,
    chatUnreadTotal: Int,
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
                Box(modifier = Modifier.weight(1f)) { LeftPane(isLoggedIn, chatUnreadTotal) }
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
                CompositionLocalProvider(
                    LocalSwipeDismiss provides swipeController,
                ) {
                    stickyOverlay?.Content()
                }
            }
        }
    }
}

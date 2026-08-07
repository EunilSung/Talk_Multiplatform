package com.eunilsung.talk.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.emoticon.EmoticonPanelController
import com.eunilsung.talk.util.Log

/** 키보드 표시 여부와 무관하게 안정적인 하단 인셋(내비바/홈인디케이터). */
@Composable
expect fun keyboardBottomInset(): Dp

/** 이모티콘 패널이 쓸 수 있는 최소 높이. */
private val MinEmoticonPanelHeight = 260.dp

/** 패널이 올라오는 시간. */
private const val PanelOpenMillis = 220

/** 패널이 내려가는 시간 — 올라올 때보다 길게 잡는다. */
private const val PanelCloseMillis = 450

/** 이모티콘 패널이 차지할 높이 — 키보드 높이를 따르되 [MinEmoticonPanelHeight] 밑으로는 안 내려간다. */
fun emoticonPanelHeight(stableImeHeight: Dp): Dp =
    maxOf(stableImeHeight, MinEmoticonPanelHeight)

@Composable
fun KeyBoardPane(
    modifier: Modifier = Modifier,
    isKeyboardVisible: Boolean,
    imeHeight: Dp,
    emoticon: EmoticonPanelController? = null,
    /** 이모티콘 패널이 열려 있을 때 예약할 높이 — [emoticonPanelHeight] 결과를 넘길 것. */
    panelHeight: Dp = 0.dp,
    fillIdleBottomInset: Boolean = false,
) {
    val bottomInset = keyboardBottomInset()
    val idleFloor = if (fillIdleBottomInset) bottomInset else 0.dp

    // 하단 높이는 목표값 하나로 정하고 그 값만 애니메이션한다.
    var prevIme by remember { mutableStateOf(imeHeight) }
    val keyboardRising = imeHeight > prevIme
    SideEffect { prevIme = imeHeight }

    // 직전에 잡고 있던 높이. 키보드가 올라오는 동안 이 아래로는 줄이지 않는다.
    var reserved by remember { mutableStateOf(idleFloor) }

    val target = when {
        // 패널이 열려 있으면 키보드가 내려가는 중이어도 패널 높이를 지킨다.
        emoticon?.isOpen == true -> maxOf(panelHeight, idleFloor)
        isKeyboardVisible && keyboardRising -> maxOf(imeHeight, reserved, idleFloor)
        // 내려가는 중엔 키보드를 그대로 따라간다.
        isKeyboardVisible -> maxOf(imeHeight, idleFloor)
        else -> idleFloor
    }

    // 키보드가 높이를 정하는 동안에는 애니메이션을 걸지 않고, 패널이 여닫힐 때만 곡선을 쓴다.
    var prevTarget by remember { mutableStateOf(target) }
    val keyboardDriven = emoticon?.isOpen != true && isKeyboardVisible
    val spec: AnimationSpec<Dp> = if (keyboardDriven) {
        snap()
    } else {
        // 줄어들 때만 느리게 — 늘어날 때까지 늘어지면 키보드보다 굼떠 보인다.
        tween(durationMillis = if (target < prevTarget) PanelCloseMillis else PanelOpenMillis)
    }
    val animatedH by animateDpAsState(targetValue = target, animationSpec = spec, label = "kbPane")

    // 키보드를 따라갈 때는 애니메이션 값을 거치지 않고 목표를 바로 쓴다.
    val h = if (keyboardDriven) target else animatedH

    SideEffect {
        prevTarget = target
        reserved = h
    }

    if (h <= 0.dp) return

    Log.message("[KeyBoardPane] h → " + h)

    Spacer(modifier = modifier.fillMaxWidth().height(h))
}

@Composable
fun EmoticonOverlay(
    emoticon: EmoticonPanelController?,
    /** 패널이 열릴 높이 — [KeyBoardPane] 에 넘긴 값과 **같아야** 한다. */
    panelHeight: Dp,
    modifier: Modifier = Modifier,
) {
    if (emoticon == null) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current

    val minH = panelHeight.coerceAtLeast(0.dp)
    val minPx = with(density) { minH.toPx() }
    val maxPx = (windowInfo.containerSize.height * 0.9f).coerceAtLeast(minPx)

    val paneHeightPx = remember { Animatable(0f) }

    LaunchedEffect(emoticon.isOpen) {
        if (emoticon.isOpen) {
            paneHeightPx.animateTo(minPx.coerceAtMost(maxPx), tween(PanelOpenMillis))
        } else {
            paneHeightPx.animateTo(0f, tween(PanelCloseMillis))
        }
    }
    LaunchedEffect(minPx) {
        if (emoticon.isOpen && paneHeightPx.value > 0f && paneHeightPx.value < minPx) {
            paneHeightPx.snapTo(minPx.coerceAtMost(maxPx))
        }
    }
    LaunchedEffect(emoticon.collapseSignal) {
        if (emoticon.collapseSignal > 0) {
            paneHeightPx.animateTo(minPx.coerceAtMost(maxPx), tween(PanelOpenMillis))
        }
    }

    if (!emoticon.isOpen && paneHeightPx.value <= 0f) return

    val hDp = with(density) { paneHeightPx.value.toDp() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(hDp)
            .background(AppColors.BgSub),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EmoticonDragHandle(
                onDrag = { dragAmountPx ->
                    scope.launch {
                        paneHeightPx.snapTo((paneHeightPx.value - dragAmountPx).coerceIn(minPx, maxPx))
                    }
                },
                onDragEnd = {
                    val target = if (paneHeightPx.value >= (minPx + maxPx) / 2f) maxPx else minPx
                    emoticon.markExpanded(target >= maxPx && maxPx > minPx)
                    scope.launch { paneHeightPx.animateTo(target, tween(PanelOpenMillis)) }
                },
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                emoticon.content?.invoke()
            }
        }
    }
}

@Composable
private fun EmoticonDragHandle(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .background(AppColors.BgSub)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        onDrag(dragAmount)
                        change.consume()
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(35.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppColors.TextSub),
        )
    }
}

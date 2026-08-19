package com.eunilsung.talk.ui.uikit.sheet

import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.pointer.blockPointerInput
import com.eunilsung.talk.ui.uikit.BackHandler
import com.eunilsung.talk.ui.uikit.click.clickable

@Composable
fun BottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    isModal: Boolean = true,
    /**
     * 콘텐츠 아래 **추가** 여백. 내비게이션바 인셋은 이 값과 별개로 항상 더해지므로,
     * 홈 인디케이터·내비게이션바를 피하려고 여기에 값을 줄 필요는 없다.
     * 기본 0 — 시트마다 필요한 여백은 콘텐츠가 직접 갖는 편이 예측 가능하다.
     */
    bottomPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    // 닫힘 통보는 **지금 조립된** 람다로 보내야 한다.
    //
    // settleSheet 는 remember 된 nestedScroll 안에 갇혀 첫 조립 때의 onDismiss 를 붙들고 있었다.
    // 그 람다는 "그때 열려 있던 시트"(=아직 없음)를 가리켜서, 닫아도 호스트가 비워지지 않았다.
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val offsetY = remember { Animatable(SHEET_HIDDEN_FALLBACK) }

    // 시트를 완전히 감추려면 "시트 높이"만큼 내려야 한다. 고정값을 쓰면 그보다 큰 시트는
    // 애니메이션이 끝난 뒤에도 아랫부분이 화면에 남아 있다가 뚝 사라진다. 실제 높이를 재서 쓴다.
    var sheetHeightPx by remember { mutableFloatStateOf(SHEET_HIDDEN_FALLBACK) }

    // Animatable.snapTo 는 suspend 라 값이 다음 프레임에야 반영된다. 한 제스처 안에서 이벤트가
    // 연달아 들어오면 낡은 offsetY 를 읽어 같은 이동을 두 번 더하게 되므로, "지금까지 명령한
    // 목표값"을 따로 들고 그걸 기준으로 누적한다.
    var dragTarget by remember { mutableFloatStateOf(0f) }

    /**
     * 닫히는 중인가.
     *
     * 닫기 애니메이션은 스프링이라 잦아드는 데 0.5~1초가 걸리고, [onDismiss] 는 그게 끝난 뒤에야
     * 불린다. 그동안 시트는 이미 화면 밖으로 나갔는데도 전체 화면 스크림이 그대로 남아 있어서,
     * 닫자마자 누른 탭이 통째로 삼켜졌다(하단 탭까지 안 먹혔다).
     *
     * 그래서 "닫기로 정해진 순간부터"는 입력을 받지 않고 아래로 흘려보낸다.
     */
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            isDismissing = false
            dragTarget = 0f
            offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        } else {
            isDismissing = true
            dragTarget = sheetHeightPx
            offsetY.animateTo(sheetHeightPx, spring(stiffness = Spring.StiffnessMediumLow))
            // 여기서는 onDismiss 를 부르지 않는다.
            //
            // 이 분기는 **바깥이 이미 닫으라고 해서** 도는 것이라, 다시 알릴 것이 없다. 예전에는
            // 불렀는데, 닫히는 애니메이션이 끝나기 전에 사용자가 다른 사용자를 누르면 그 통보가
            // 갓 열린 시트를 도로 닫아 버렸다 — 클릭은 먹었는데 시트가 안 올라오던 증상이다.
            //
            // 스스로 닫는 경로(끌기·스크림·뒤로가기)는 각자 onDismiss 를 부르므로 누락은 없다.
        }
    }

    // 높이를 처음 알게 된 시점에 숨은 위치를 맞춰 둔다 — 첫 열기 때 시트 아랫단이 화면에
    // 걸친 채로 슬라이드가 시작되지 않도록. 진행 중인 애니메이션은 건드리지 않는다.
    LaunchedEffect(sheetHeightPx) {
        if (!isVisible && !offsetY.isRunning) {
            dragTarget = sheetHeightPx
            offsetY.snapTo(sheetHeightPx)
        }
    }

    /**
     * 닫기로 정하는 **즉시** 호스트에 알린다. 내려가는 애니메이션은 `isVisible` 이 false 가 되면
     * 위쪽 [LaunchedEffect] 가 맡는다.
     *
     * 예전에는 애니메이션이 끝난 뒤에 알렸는데, 그 0.5~1초 사이에 사용자가 다른 사람을 누르면
     * 뒤늦은 통보가 갓 열린 시트를 도로 닫아 버렸다.
     */
    val closeSheetWithAnimation = {
        isDismissing = true
        currentOnDismiss()
    }

    /** 시트를 [delta] 만큼 끌어내리고 **실제로 소비한 양**을 돌려준다. 위쪽 한계는 0. */
    fun dragSheetBy(delta: Float): Float {
        val next = (dragTarget + delta).coerceAtLeast(0f)
        val consumed = next - dragTarget
        if (consumed != 0f) {
            dragTarget = next
            scope.launch { offsetY.snapTo(next) }
        }
        return consumed
    }

    /** 손을 뗐을 때 — 충분히 내렸거나 아래로 튕겼으면 닫고, 아니면 제자리로 되돌린다. */
    suspend fun settleSheet(velocityY: Float) {
        if (dragTarget > SHEET_DISMISS_DRAG_PX || velocityY > SHEET_DISMISS_VELOCITY) {
            isDismissing = true
            currentOnDismiss()
        } else {
            dragTarget = 0f
            offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
        }
    }

    /**
     * 이번 손짓에서 내부 목록이 스크롤을 한 번이라도 소비했는지.
     *
     * 조립 중에 읽지 않으므로 값이 바뀌어도 재구성되지 않는다.
     */
    val innerScrolled = remember { mutableStateOf(false) }

    /**
     * 시트 안 스크롤과 시트 끌기를 잇는다.
     *
     * 내부 목록이 맨 위에 닿아 더 못 내려가면 그 남은 만큼을 시트가 받아 내려가고([onPostScroll]),
     * 시트가 내려간 상태에서 다시 위로 올리면 목록보다 시트가 먼저 제자리로 돌아온다([onPreScroll]).
     * 이게 없으면 내부 verticalScroll 이 드래그를 먼저 삼켜 시트가 꿈쩍도 하지 않는다.
     *
     * 다만 **한 손짓 안에서 목록을 스크롤한 뒤 이어서 시트까지 끌리지는 않게** 막는다. 그러지
     * 않으면 아래쪽을 보다가 위로 되돌리는 동작 하나가 맨 위 도달과 동시에 시트를 닫아 버린다.
     * 목록을 스크롤했다면 그 손짓은 목록의 것이고, 시트를 닫으려면 손을 떼고 다시 끌어야 한다.
     */
    val sheetNestedScroll = remember(offsetY) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta >= 0f || dragTarget <= 0f) return Offset.Zero
                return Offset(0f, dragSheetBy(delta))
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y != 0f) innerScrolled.value = true

                val delta = available.y
                if (delta <= 0f) return Offset.Zero
                // 이미 끌리고 있는 시트는 계속 따라와야 한다 — 손짓 도중에 멈춰 버리면 안 된다.
                if (innerScrolled.value && dragTarget <= 0f) return Offset.Zero
                return Offset(0f, dragSheetBy(delta))
            }

            // 시트가 내려가 있는 동안의 관성은 목록이 아니라 시트 몫이다 — 전부 소비하고 정리한다.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragTarget <= 0f) return Velocity.Zero
                settleSheet(available.y)
                return available
            }

            /** 손짓이 끝나는 지점. 다음 손짓은 다시 시트를 끌 수 있어야 한다. */
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                innerScrolled.value = false
                return Velocity.Zero
            }
        }
    }

    if (isVisible) {
        BackHandler(enabled = true) {
            closeSheetWithAnimation()
        }
    }

    // offsetY.value 를 조건식에서 직접 읽으면 열기/닫기 300ms + 드래그 프레임마다
    // 시트 전체가 재구성된다. 표시 여부만 파생 상태로 좁힌다.
    val isRendered by remember(offsetY) { derivedStateOf { offsetY.value < sheetHeightPx } }
    if (isVisible || isRendered) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 알파 계산을 draw 단계로 내려 스크림 때문에 재구성되지 않게 한다.
                .drawBehind {
                    if (!isModal) return@drawBehind
                    val progress = offsetY.value / sheetHeightPx.coerceAtLeast(1f)
                    val alpha = (SCRIM_MAX_ALPHA * (1f - progress))
                        .coerceIn(0f, SCRIM_MAX_ALPHA)
                    drawRect(Color.Black, alpha = alpha)
                }
                .then(
                    when {
                        // 이미 닫기로 정해졌다. 남은 애니메이션 동안 누른 것은 뒤 화면 몫이다.
                        isDismissing -> Modifier
                        isModal -> Modifier
                            .clickable(
                                rippleColor = null,
                                onClick = { closeSheetWithAnimation() }
                            )
                        // 비모달은 스크림 탭으로 닫지 않지만, 입력까지 흘려보내면 시트가 덮지 못한
                        // 위쪽을 눌렀을 때 뒤 화면이 반응한다. 닫히지는 않되 통과도 막는다.
                        else -> Modifier.blockPointerInput()
                    }
                )
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                    .nestedScroll(sheetNestedScroll)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { scope.launch { settleSheet(0f) } },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragSheetBy(dragAmount)
                            }
                        )
                    }
                    .clickable(
                        rippleColor = null,
                        onClick = { }
                    ),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = AppColors.BgSub,
                shadowElevation = if (isModal) 0.dp else 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .background(AppColors.LightGray.copy(alpha = 0.5f), CircleShape)
                    )
                    content()
                    Spacer(
                        modifier = Modifier.height(
                            bottomPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BottomSheetPreview() {
    MaterialTheme {
        BottomSheet(
            true,
            {},
            true,
            content = {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .background(color = MaterialTheme.colorScheme.background)
                )
            }
        )
    }
}

/** 높이를 재기 전에 쓰는 숨김 오프셋(px). 측정되면 실제 시트 높이로 대체된다. */
private const val SHEET_HIDDEN_FALLBACK = 2000f

/** 이만큼 끌어내리면 닫는다(px). */
private const val SHEET_DISMISS_DRAG_PX = 300f

/** 덜 내렸어도 이보다 빠르게 아래로 튕기면 닫는다(px/s). */
private const val SHEET_DISMISS_VELOCITY = 1200f

/** 모달 스크림 최대 불투명도. */
private const val SCRIM_MAX_ALPHA = 0.4f

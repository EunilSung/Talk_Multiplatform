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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
    val offsetY = remember { Animatable(2000f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        } else {
            offsetY.animateTo(2000f, spring(stiffness = Spring.StiffnessMediumLow))
            onDismiss()
        }
    }

    val closeSheetWithAnimation = {
        scope.launch {
            offsetY.animateTo(2000f, spring(stiffness = Spring.StiffnessMediumLow))
            onDismiss()
        }
    }

    if (isVisible) {
        BackHandler(enabled = true) {
            closeSheetWithAnimation()
        }
    }

    // offsetY.value 를 조건식에서 직접 읽으면 열기/닫기 300ms + 드래그 프레임마다
    // 시트 전체가 재구성된다. 표시 여부만 파생 상태로 좁힌다.
    val isRendered by remember(offsetY) { derivedStateOf { offsetY.value < SHEET_HIDDEN_OFFSET } }
    if (isVisible || isRendered) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 알파 계산을 draw 단계로 내려 스크림 때문에 재구성되지 않게 한다.
                .drawBehind {
                    if (!isModal) return@drawBehind
                    val alpha = (SCRIM_MAX_ALPHA * (1f - (offsetY.value / 1000f)))
                        .coerceIn(0f, SCRIM_MAX_ALPHA)
                    drawRect(Color.Black, alpha = alpha)
                }
                .then(
                    if (isModal) {
                        Modifier
                            .clickable(
                                rippleColor = null,
                                onClick = { closeSheetWithAnimation() }
                            )
                    } else {
                        // 비모달은 스크림 탭으로 닫지 않지만, 입력까지 흘려보내면 시트가 덮지 못한
                        // 위쪽을 눌렀을 때 뒤 화면이 반응한다. 닫히지는 않되 통과도 막는다.
                        Modifier.blockPointerInput()
                    }
                )
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (offsetY.value > SHEET_DISMISS_DRAG_PX) {
                                        closeSheetWithAnimation()
                                    } else {
                                        offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                                    }
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    val newValue = (offsetY.value + dragAmount).coerceAtLeast(0f)
                                    offsetY.snapTo(newValue)
                                }
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

/** 시트가 화면 밖으로 완전히 내려간 오프셋(px). */
private const val SHEET_HIDDEN_OFFSET = 2000f

/** 이만큼 끌어내리면 닫는다(px). */
private const val SHEET_DISMISS_DRAG_PX = 300f

/** 모달 스크림 최대 불투명도. */
private const val SCRIM_MAX_ALPHA = 0.4f

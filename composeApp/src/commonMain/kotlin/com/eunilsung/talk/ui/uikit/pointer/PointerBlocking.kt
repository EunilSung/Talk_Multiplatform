package com.eunilsung.talk.ui.uikit.pointer

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 이 레이어에서 히트테스트를 끊어 **뒤쪽 형제로 입력이 내려가지 않게** 한다.
 *
 * Compose 는 겹쳐 그린 형제 중 위쪽이 "히트"되면 아래 형제를 히트테스트하지 않는데,
 * 그 판정 기준은 이벤트 소비가 아니라 **그 좌표에 PointerInput 노드가 있는지**다.
 * 그래서 `background()` 만 걸린 Box 는 시각적으로만 가릴 뿐 탭·드래그가 그대로 뒤로 전달된다.
 *
 * 빈 [pointerInput] 블록은 노드만 등록하고 아무것도 소비하지 않으므로,
 * 뒤쪽은 막으면서 **자기 자식의 클릭·스크롤·드래그는 그대로 동작**한다.
 * (Material3 `Surface` 가 쓰는 방식과 같다 — Scaffold 를 루트로 둔 화면들이 우연히 안전한 이유.)
 */
fun Modifier.blockPointerInput(): Modifier = pointerInput(Unit) {}

/**
 * 남은 포인터 이벤트까지 **소비**한다 — 잠금 화면처럼 뒤쪽이 절대 반응하면 안 되는 경우.
 *
 * 소비는 자식이 먼저 처리하는 Main 패스에서 일어나므로 자기 자식의 입력은 살아 있고,
 * 자식이 쓰고 남은 것만 흡수한다. [blockPointerInput] 보다 강하지만,
 * 자식 제스처가 이벤트를 소비하지 않는 구조라면 영향이 있을 수 있으니 기본은 [blockPointerInput] 을 쓴다.
 */
fun Modifier.consumeAllPointerEvents(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { if (!it.isConsumed) it.consume() }
        }
    }
}

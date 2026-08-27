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


package com.eunilsung.talk.ui.chatroom

import androidx.compose.runtime.Immutable
import com.eunilsung.talk.domain.model.Notice

/**
 * 공지바에 필요한 상태와 콜백 묶음.
 *
 * 공지 하나를 그리는 데 값 2개와 콜백 4개가 필요해 화면 파라미터가 여섯 갈래로 늘어난다.
 * 함께 움직이는 값들이라 [MediaPickerBindings][com.eunilsung.talk.ui.uikit.mediapicker.MediaPickerBindings]
 * 처럼 한 덩어리로 넘긴다. 기본값이 모두 있어 `@Preview` 에서는 인자 없이 쓸 수 있다.
 */
@Immutable
data class NoticeBindings(
    /** 현재 걸린 공지. null 이면 공지바를 그리지 않는다. */
    val notice: Notice? = null,
    /** 접힘·상세표시·영구숨김 — Settings 에 영속되며 VM 이 소유한다. */
    val barState: NoticeBarUiState = NoticeBarUiState(),
    /** 공지가 바뀔 때 저장된 표시 상태를 읽어 온다. */
    val onLoad: (identityKey: String) -> Unit = {},
    val onExpandedChange: (identityKey: String, expanded: Boolean) -> Unit = { _, _ -> },
    val onDetailsChange: (identityKey: String, shown: Boolean) -> Unit = { _, _ -> },
    val onHide: (identityKey: String) -> Unit = {},
)

/** 공지바의 접힘·상세표시·영구숨김 상태. */
data class NoticeBarUiState(
    val hidden: Boolean = false,
    val expanded: Boolean = true,
    val showDetails: Boolean = true,
)

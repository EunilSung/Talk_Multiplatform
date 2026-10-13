package com.eunilsung.talk.ui.chatroom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eunilsung.talk.Config
import com.eunilsung.talk.domain.model.PolishStyle
import com.eunilsung.talk.ui.chatroom.input.MentionFieldSnapshot
import com.eunilsung.talk.ui.chatroom.input.MentionFieldState
import com.eunilsung.talk.ui.chatroom.input.PolishButtonState
import com.eunilsung.talk.ui.uikit.dialog.ChatListDialog
import com.eunilsung.talk.ui.uikit.TestTags

/**
 * 입력창의 글 다듬기 — 다듬기 방식 메뉴, 다듬은 글 반영, 되돌리기.
 *
 * 다듬은 글은 요청할 때의 입력창 글이 그대로일 때만 덮어쓴다. 덮어쓴 직후 입력창을 손대지 않았으면
 * 버튼이 되돌리기로 바뀌어 원래 글로 돌아갈 수 있다.
 */
@Stable
internal class PolishInputState(private val mentionState: MentionFieldState) {

    var isMenuOpen by mutableStateOf(false)

    private var undo by mutableStateOf<PolishUndo?>(null)

    private val canUndo: Boolean get() = undo?.polishedText == mentionState.text

    /** 입력창 옆 다듬기 버튼의 모양. 서버가 없으면 숨긴다. */
    fun buttonState(polish: PolishUiState): PolishButtonState = when {
        !Config.Server.IS_ENABLED -> PolishButtonState.HIDDEN
        polish == PolishUiState.Loading -> PolishButtonState.LOADING
        canUndo -> PolishButtonState.UNDO
        mentionState.text.trim().length >= MIN_POLISH_LENGTH -> PolishButtonState.POLISH
        else -> PolishButtonState.HIDDEN
    }

    /** 버튼을 눌렀다 — 되돌릴 수 있으면 원래 글로 돌리고, 아니면 다듬기 방식 메뉴를 연다. */
    fun onButtonClick() {
        val current = undo
        if (canUndo && current != null) {
            mentionState.restore(current.original)
            undo = null
        } else {
            isMenuOpen = true
        }
    }

    /** 다듬은 글을 받았다. 그 사이 입력창이 바뀌었으면 덮어쓰지 않는다. */
    fun apply(ready: PolishUiState.Ready) {
        if (mentionState.text != ready.sourceText) return
        val original = mentionState.snapshot()
        mentionState.replaceText(ready.text)
        undo = PolishUndo(original, mentionState.text)
    }
}

@Composable
internal fun rememberPolishInputState(mentionState: MentionFieldState): PolishInputState =
    remember(mentionState) { PolishInputState(mentionState) }

/** 다듬기 방식 메뉴 — 맞춤법·공손하게·간결하게. */
@Composable
internal fun PolishStyleMenu(
    strings: ChatRoomStrings,
    onDismiss: () -> Unit,
    onStyleSelected: (PolishStyle) -> Unit,
) {
    ChatListDialog(
        list = listOf(strings.polishCorrect, strings.polishPolite, strings.polishConcise),
        showEmpathy = false,
        optionTags = mapOf(strings.polishPolite to TestTags.POLISH_POLITE),
        onDismiss = onDismiss,
        onOptionSelected = { picked ->
            val style = when (picked) {
                strings.polishPolite -> PolishStyle.POLITE
                strings.polishConcise -> PolishStyle.CONCISE
                else -> PolishStyle.CORRECT
            }
            onStyleSelected(style)
        },
    )
}

/** 다듬기 전의 입력창과 다듬은 글. 입력창이 [polishedText] 그대로일 때만 되돌릴 수 있다. */
private data class PolishUndo(val original: MentionFieldSnapshot, val polishedText: String)

/** 이보다 짧은 글("넵", "ㅇㅇ")에는 다듬기 버튼을 내놓지 않는다. */
private const val MIN_POLISH_LENGTH = 5

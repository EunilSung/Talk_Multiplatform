package com.eunilsung.talk.ui.uikit

import androidx.compose.ui.Modifier

/**
 * 화면 자동화(Maestro)가 요소를 찾는 이름. `.maestro` 의 흐름 파일이 같은 문자열을 `id` 로 쓴다.
 *
 * 화면 문구는 기기 언어에 따라 바뀌고 같은 문구가 여러 곳에 나오므로, 흐름이 누르고 확인하는 요소는 이 이름으로 찾는다.
 * 이름을 바꾸면 흐름 파일도 함께 바꾼다.
 */
object TestTags {
    const val LOGIN_ID = "login_id"
    const val LOGIN_PW = "login_pw"
    const val LOGIN_SUBMIT = "login_submit"

    const val CHAT_ROOM_ITEM = "chat_room_item"
    const val CHAT_ROOM_UNREAD = "chat_room_unread"

    const val CHAT_ROOM_BACK = "chat_room_back"
    const val CHAT_ROOM_MENU = "chat_room_menu"
    const val CHAT_INPUT = "chat_input"
    const val CHAT_SEND = "chat_send"
    const val CHAT_EMPATHY = "chat_empathy"
    const val CHAT_MENU_TRANSLATE = "chat_menu_translate"
    const val CHAT_MENU_FORWARD = "chat_menu_forward"
    const val SHARE_SUBMIT = "share_submit"
    const val SHARE_TAB_CHATROOM = "share_tab_chatroom"
    /** 받아 온 번역문. 받는 중에는 붙지 않는다. */
    const val CHAT_TRANSLATION = "chat_translation"
    const val CHAT_POLISH = "chat_polish"
    /** 다듬은 글로 바뀐 뒤의 되돌리기 버튼. */
    const val CHAT_POLISH_UNDO = "chat_polish_undo"
    const val POLISH_POLITE = "polish_polite"

    const val DRAWER_VOTE = "drawer_vote"

    const val VOTE_CREATE = "vote_create"
    const val VOTE_TITLE = "vote_title"
    const val VOTE_SUBMIT_CREATE = "vote_submit_create"
    const val VOTE_SUBMIT = "vote_submit"
    const val VOTE_TOTAL = "vote_total"
    const val VOTE_CLOSE = "vote_close"

    /** 목록 다이얼로그에서 되돌릴 수 없는 항목(나가기). */
    const val LIST_DIALOG_DESTRUCTIVE = "list_dialog_destructive"
    const val DIALOG_CONFIRM = "dialog_confirm"

    /** 하단 탭. [key] 는 탭의 상태 보존 키(group·chat·setting)다. */
    fun tab(key: String): String = "tab_$key"

    /** 대화 메뉴의 [index] 번째 공감. */
    fun chatMenuEmpathy(index: Int): String = "chat_menu_empathy_$index"

    /** 투표 만들기의 [index] 번째 항목 입력칸(0부터). */
    fun voteOption(index: Int): String = "vote_option_$index"
}

/**
 * 이 아래에 붙은 testTag 를 화면 자동화가 찾을 수 있게 내보낸다.
 *
 * Android 는 testTag 를 resource-id 로 내보내라고 알려야 하고, 다이얼로그는 창이 따로라 다이얼로그마다 붙인다.
 * iOS 는 testTag 가 그대로 접근성 식별자로 나가 할 일이 없다.
 */
expect fun Modifier.exposeTestTags(): Modifier

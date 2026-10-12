package com.eunilsung.talk.ui.chatroom.linkpreview

sealed interface LinkPreviewActions {
    /** 이 주소의 카드가 화면에 나왔다. 미리보기를 받아 와 상태에 붙인다. */
    data class Load(val url: String) : LinkPreviewActions
}

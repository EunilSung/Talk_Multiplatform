package com.eunilsung.talk.ui.chatroom.linkpreview

sealed interface LinkPreviewActions {
    /**
     * 이 주소의 카드가 화면에 나왔다. [isMe] 는 내가 보낸 대화인지다. 받을지는 ViewModel 이 정한다 —
     * 내가 보낸 대화만 바로 받고, 받은 대화는 '미리보기 보기'를 누를 때까지 기다린다. 받으면 안 되는 주소는 카드를 숨긴다.
     */
    data class OnShown(val url: String, val isMe: Boolean) : LinkPreviewActions

    /** '미리보기 보기'를 눌렀다. 미리보기를 받아 와 상태에 붙인다. */
    data class Load(val url: String) : LinkPreviewActions
}

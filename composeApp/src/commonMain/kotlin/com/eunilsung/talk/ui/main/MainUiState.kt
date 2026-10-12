package com.eunilsung.talk.ui.main

import com.eunilsung.talk.domain.model.ProfileImageUrls
import com.eunilsung.talk.domain.model.User

/** 메인 화면의 상태 — 로그인·연결 상태, 하단 탭 배지, 앱 전체가 쓰는 프로필 사진 주소. */
data class MainUiState(
    val isLoggedIn: Boolean = false,
    val isConnected: Boolean = true,
    /** 대화 탭 배지에 붙는 모든 대화방의 안읽음 합. */
    val chatUnreadTotal: Int = 0,
    val profileImages: ProfileImageUrls = ProfileImageUrls.EMPTY,
    /** 로그인한 나. 워터마크에 아이디·이름을 쓴다. 로그인 전이면 빈 값이다. */
    val me: User = User(),
) {
    /** 로그인한 채로 서버와 끊겨 다시 붙는 중이다. 화면 위에 띠를 띄운다. */
    val isReconnecting: Boolean get() = isLoggedIn && !isConnected
}
